package com.webflux.binance.config;

import com.webflux.binance.handler.BinanceWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;

import java.util.Map;

@Configuration
public class WebConfig {

    @Bean
    public HandlerMapping handlerMapping(BinanceWebSocketHandler binanceWebSocketHandler) {
        Map<String, WebSocketHandler> map = Map.of(
                "/binance", binanceWebSocketHandler
        );
        int order = -1;
        return new SimpleUrlHandlerMapping(map, order);
    }
}
