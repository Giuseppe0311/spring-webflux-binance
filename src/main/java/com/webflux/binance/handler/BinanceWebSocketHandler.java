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


@Service
@RequiredArgsConstructor
@Slf4j
public class BinanceWebSocketHandler implements WebSocketHandler {

    private final BinanceClient binanceClient;
    private final ObjectMapper objectMapper;
    private final ExchangeService exchangeService;



    @Override
    public Mono<Void> handle(WebSocketSession session) {
        Flux<WebSocketMessage> outbound = binanceClient.messageStream()
                .flatMap(message ->
                        Mono.fromCallable(() -> objectMapper.readValue(message, BinanceResponse.class))
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
                                                        .onErrorContinue((throwable, o) ->
                                                                log.error("Error procesando message: {}", o, throwable)
                                                        )
                                        )
                                )
                                .onErrorContinue((throwable, o) ->
                                        log.error("Error procesando message: {}", o, throwable)
                                )
                );

        Mono<Void> inbound = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(System.out::println)
                .doOnNext(binanceClient::sendControl)
                .then();
        return Mono.when(
                session.send(outbound),
                inbound
        );
    }
}
