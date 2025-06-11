package com.webflux.binance.dto;

import java.util.List;

public record BinanceRequest(
        String method,
        List<String> params,
        String id
) {
}
