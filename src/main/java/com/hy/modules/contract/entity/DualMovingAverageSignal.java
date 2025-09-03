package com.hy.modules.contract.entity;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * 双均线交易信号实体类
 **/
@Getter
@Setter
@ToString
@NoArgsConstructor
public class DualMovingAverageSignal {

    /**
     * 交易对 BTCUSDT、ETHUSDT、SOL 等
     */
    private String symbol;

    /**
     * 均线最高价
     */
    private BigDecimal highPrice;

    /**
     * 均线最低价
     */
    private BigDecimal lowPrice;

    public DualMovingAverageSignal(String symbol, BigDecimal highPrice, BigDecimal lowPrice) {
        this.symbol = symbol;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
    }
}
