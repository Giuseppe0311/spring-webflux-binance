package com.webflux.binance.client;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.WebsocketClientSpec;
import reactor.util.retry.Retry;

import java.time.Duration;


@Component
@RequiredArgsConstructor
@Slf4j
public class BinanceClient {

    @Value("${clients.binance.websocket.url}")
    private String webSocketUrl;

    private final HttpClient httpClient;

    private final Sinks.Many<String> controlSink =
            Sinks.many().multicast().onBackpressureBuffer();

    private final Sinks.Many<String> messageSink =
            Sinks.many().replay().limit(50);


    private volatile boolean isConnected = false;
    private static final int MAX_RETRIES = 10;
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(1);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(2);

    @PostConstruct
    public void init() {
        connect().subscribe();
    }
    
    private Mono<Void> connect() {
        log.info("Initiating WebSocket connection to Binance...");
        
        return httpClient
                .websocket(WebsocketClientSpec.builder().maxFramePayloadLength(256 * 1024).build())
                .uri(webSocketUrl + "/ws")
                .handle((inbound, outbound) -> {
                    log.info("WebSocket connection established with Binance");
                    isConnected = true;
                    
                    // Monitor connection state
                    inbound.withConnection(conn -> 
                        conn.onDispose(() -> {
                            log.warn("WebSocket connection to Binance closed");
                            isConnected = false;
                        })
                    );
                    
                    Mono<Void> receive = inbound.receive()
                            .asString()
                            .doOnNext(message -> {
                                log.debug("Received message from Binance: {}", message);
                                messageSink.tryEmitNext(message);
                            })
                            .then();

                    Mono<Void> send = outbound
                            .sendString(controlSink.asFlux())
                            .then();

                    return Mono.when(receive, send);
                })
                .doOnError(error -> {
                    log.error("Error in Binance WebSocket connection: {}", error.getMessage(), error);
                    isConnected = false;
                    messageSink.tryEmitNext("{\"error\":\"Connection to Binance failed. Attempting to reconnect...\"}");
                })
                .retryWhen(Retry.backoff(MAX_RETRIES, INITIAL_BACKOFF)
                        .maxBackoff(MAX_BACKOFF)
                        .doBeforeRetry(retrySignal -> 
                            log.info("Attempting to reconnect to Binance WebSocket. Attempt: {}", retrySignal.totalRetries() + 1)
                        )
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                            log.error("Failed to connect to Binance WebSocket after {} attempts", MAX_RETRIES);
                            return new RuntimeException("Failed to connect to Binance WebSocket after maximum retry attempts", retrySignal.failure());
                        })
                );
    }


    public Flux<String> messageStream() {
        return messageSink.asFlux();
    }

    public void sendControl(String message) {
        Sinks.EmitResult r = controlSink.tryEmitNext(message);
        if (r.isFailure()) log.warn("controlSink emit failed: {}", r);
    }
    
    /**
     * Manually reconnect to the Binance WebSocket.
     * This can be called if the automatic reconnection fails after maximum retries.
     * 
     * @return A Mono that completes when the reconnection attempt is finished
     */
    public Mono<Void> reconnect() {
        log.info("Manual reconnection to Binance WebSocket requested");
        if (isConnected) {
            log.info("Already connected to Binance WebSocket, no need to reconnect");
            return Mono.empty();
        }
        
        return connect()
                .doOnSubscribe(s -> log.info("Starting manual reconnection to Binance WebSocket"))
                .doOnSuccess(v -> log.info("Manual reconnection to Binance WebSocket successful"))
                .doOnError(e -> log.error("Manual reconnection to Binance WebSocket failed", e));
    }
    
    /**
     * Check if the client is currently connected to the Binance WebSocket
     * 
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return isConnected;
    }


}
