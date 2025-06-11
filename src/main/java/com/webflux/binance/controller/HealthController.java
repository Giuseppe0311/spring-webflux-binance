package com.webflux.binance.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class HealthController {
    @GetMapping("/")
    public Mono<String> healthCheck() {
        return Mono.just("OK");
    }
}
