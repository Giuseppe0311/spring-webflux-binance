package com.webflux.binance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SUNATResponse(
        Boolean success,
        String errorDescription,
        BigDecimal compra,
        BigDecimal venta,
        String origen,
        String moneda,
        LocalDate fecha
) {
}
