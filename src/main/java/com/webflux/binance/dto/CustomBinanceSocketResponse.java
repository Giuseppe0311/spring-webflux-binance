package com.webflux.binance.dto;

import com.webflux.binance.constant.TradeSite;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record CustomBinanceSocketResponse(
        String symbol,
        TradeSite side,
        TradeValue usd,
        TradeValue pen,
        BigDecimal dollarPenValue,
        BigDecimal quantity,
        Instant tradeTime,
        Long tradeId
) {
}
