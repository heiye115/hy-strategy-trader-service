package com.hy.modules.cex.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class ShortTermPrice {

    /**
     * 交易对
     **/
    private String symbol;

    /**
     * 最高价格时间戳（毫秒）
     */
    private Long highPriceTimestamp;

    /**
     * 最高价格
     **/
    private BigDecimal highPrice;

    /**
     * 最低价格时间戳（毫秒）
     */
    private Long lowPriceTimestamp;

    /**
     * 最低价格
     **/
    private BigDecimal lowPrice;


    /**
     * 最高均价
     **/
    private BigDecimal highAveragePrice;

    /**
     * 最低均价
     **/
    private BigDecimal lowAveragePrice;


    public ShortTermPrice(String symbol, Long highPriceTimestamp, BigDecimal highPrice, Long lowPriceTimestamp, BigDecimal lowPrice, BigDecimal highAveragePrice, BigDecimal lowAveragePrice) {
        this.symbol = symbol;
        this.highPriceTimestamp = highPriceTimestamp;
        this.highPrice = highPrice;
        this.lowPriceTimestamp = lowPriceTimestamp;
        this.lowPrice = lowPrice;
        this.highAveragePrice = highAveragePrice;
        this.lowAveragePrice = lowAveragePrice;
    }

}
