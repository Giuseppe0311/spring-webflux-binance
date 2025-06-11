package com.webflux.binance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

public record BinanceResponse(
        String e,
        Instant E,
        String s,
        long t,
        BigDecimal p,
        BigDecimal q,
        Instant T,
        boolean m
) {
}
