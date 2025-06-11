package com.webflux.binance.dto;

import java.math.BigDecimal;

public record TradeValue(
        BigDecimal unitPrice,
        BigDecimal total
) {
}
