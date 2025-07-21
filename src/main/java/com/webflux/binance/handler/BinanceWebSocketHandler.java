package com.webflux.binance.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webflux.binance.client.BinanceClient;
import com.webflux.binance.constant.TradeSite;
import com.webflux.binance.dto.BinanceResponse;
import com.webflux.binance.dto.CustomBinanceSocketResponse;
import com.webflux.binance.dto.TradeValue;
import com.webflux.binance.service.ExchangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Slf4j
public class BinanceWebSocketHandler implements WebSocketHandler {

    private final BinanceClient binanceClient;
    private final ObjectMapper objectMapper;
    private final ExchangeService exchangeService;


    // Connection monitoring interval in seconds
    private static final Duration CONNECTION_CHECK_INTERVAL = Duration.ofSeconds(30);
    
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // Setup connection monitoring
        Flux<Long> connectionMonitor = Flux.interval(CONNECTION_CHECK_INTERVAL)
                .doOnNext(tick -> {
                    if (!binanceClient.isConnected()) {
                        log.warn("Detected Binance WebSocket disconnection. Attempting manual reconnection...");
                        binanceClient.reconnect()
                                .subscribe(
                                        null,
                                        error -> log.error("Manual reconnection failed", error),
                                        () -> {
                                            log.info("Manual reconnection completed");
                                            // Notify client about reconnection
                                            try {
                                                String reconnectMsg = objectMapper.writeValueAsString(
                                                        Map.of("type", "system", "message", "Reconnected to Binance WebSocket")
                                                );
                                                session.send(Mono.just(session.textMessage(reconnectMsg))).subscribe();
                                            } catch (Exception e) {
                                                log.error("Error sending reconnection notification", e);
                                            }
                                        }
                                );
                    }
                });
        
        // Process messages from Binance
        Flux<WebSocketMessage> outbound = binanceClient.messageStream()
                .flatMap(message -> {
                    // Check if it's an error message from our client
                    if (message.startsWith("{\"error\":")) {
                        try {
                            return Mono.just(session.textMessage(message));
                        } catch (Exception e) {
                            log.error("Error forwarding error message to client", e);
                            return Mono.empty();
                        }
                    }
                    
                    // Process normal message
                    return Mono.fromCallable(() -> objectMapper.readValue(message, BinanceResponse.class))
                            .subscribeOn(Schedulers.parallel())
                            .filter(resp ->
                                    resp.s() != null &&
                                            resp.p() != null &&
                                            resp.q() != null &&
                                            resp.T() != null
                            )
                            .flatMap(binanceResponse ->
                                    exchangeService.getUSDtoPENExchangeRate().flatMap(sunatResponse ->
                                            Mono.fromCallable(
                                                            () -> {
                                                                BigDecimal totalDollars = binanceResponse.p().multiply(binanceResponse.q());
                                                                CustomBinanceSocketResponse custom = new CustomBinanceSocketResponse(
                                                                        binanceResponse.s(),
                                                                        binanceResponse.m() ? TradeSite.BUY : TradeSite.SELL,
                                                                        new TradeValue(binanceResponse.p(), totalDollars),
                                                                        new TradeValue(
                                                                                binanceResponse.p().multiply(sunatResponse.compra()),
                                                                                totalDollars.multiply(sunatResponse.compra())
                                                                        ),
                                                                        sunatResponse.compra(),
                                                                        binanceResponse.q(),
                                                                        binanceResponse.T(),
                                                                        binanceResponse.t()
                                                                );
                                                                String json = objectMapper.writeValueAsString(custom);
                                                                return session.textMessage(json);
                                                            }
                                                    )
                                                    .doOnError(throwable -> log.error("Error procesando message: {}", message, throwable))
                                                    .subscribeOn(Schedulers.parallel())
                                                    .onErrorContinue((throwable, o) -> {
                                                        log.error("Error procesando message: {}", o, throwable);
                                                        // Notify client about error
                                                        try {
                                                            String errorMsg = objectMapper.writeValueAsString(
                                                                    Map.of("type", "error", "message", "Error processing message: " + throwable.getMessage())
                                                            );
                                                            session.send(Mono.just(session.textMessage(errorMsg))).subscribe();
                                                        } catch (Exception e) {
                                                            log.error("Error sending error notification", e);
                                                        }
                                                    })
                                    )
                            )
                            .onErrorContinue((throwable, o) -> {
                                log.error("Error procesando message: {}", o, throwable);
                                // Notify client about error
                                try {
                                    String errorMsg = objectMapper.writeValueAsString(
                                            Map.of("type", "error", "message", "Error processing message: " + throwable.getMessage())
                                    );
                                    session.send(Mono.just(session.textMessage(errorMsg))).subscribe();
                                } catch (Exception e) {
                                    log.error("Error sending error notification", e);
                                }
                            });
                });

        // Handle incoming messages from client
        Mono<Void> inbound = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(message -> {
                    log.debug("Received message from client: {}", message);
                    binanceClient.sendControl(message);
                })
                .then();
                
        // Combine all streams
        return Mono.when(
                session.send(outbound),
                inbound,
                connectionMonitor.then()
        );
    }
}
