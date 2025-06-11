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


    @PostConstruct
    public void init() {
        Flux<Void> connectionStream = httpClient
                .websocket(WebsocketClientSpec.builder().maxFramePayloadLength(256 * 1024).build())
                .uri(webSocketUrl + "/ws")
                .handle((inbound, outbound) -> {
                    Mono<Void> receive = inbound.receive()
                            .asString()
                            .doOnNext(messageSink::tryEmitNext)
                            .then();

                    Mono<Void> send = outbound
                            .sendString(controlSink.asFlux())
                            .then();

                    return Mono.when(receive, send);
                })
                .publish()
                .autoConnect(1);

        connectionStream.subscribe();
    }


    public Flux<String> messageStream() {
        return messageSink.asFlux();
    }

      public void sendControl(String message) {
        Sinks.EmitResult r = controlSink.tryEmitNext(message);
        if (r.isFailure()) log.warn("controlSink emit failed: {}", r);
    }


}
