package com.webflux.binance.service;

import com.webflux.binance.dto.SUNATResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ExchangeService {

    private final Mono<SUNATResponse> dailyRate;

    public ExchangeService(WebClient webClient) {
        this.dailyRate = webClient.get()
                .uri("https://api.apis.net.pe/v1/tipo-cambio-sunat")
                .retrieve()
                .bodyToMono(SUNATResponse.class)
                .map(resp -> new SUNATResponse(
                        true, null,
                        resp.compra(), resp.venta(),
                        resp.origen(), resp.moneda(),
                        resp.fecha()
                ))
                .onErrorResume(err -> Mono.just(new SUNATResponse(
                        false, err.getMessage(),
                        BigDecimal.valueOf(3.65), BigDecimal.valueOf(3.65),
                        "DEFAULT", "PEN", LocalDate.now()
                )))
                .cache(Duration.ofHours(24));
    }

    public Mono<SUNATResponse> getUSDtoPENExchangeRate() {
        return dailyRate;
    }
}
