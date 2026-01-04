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
public class RangePrice {

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
     * 均价
     **/
    private BigDecimal averagePrice;

    /**
     * 最高均价
     **/
    private BigDecimal highAveragePrice;

    /**
     * 最低均价
     **/
    private BigDecimal lowAveragePrice;

    /**
     * 区间数
     **/
    private Integer rangeCount;

    /**
     * 最高价格240K线时间戳（毫秒）
     */
    private Long highPriceTimestamp240;

    /**
     * 最低价格240K线时间戳（毫秒）
     */
    private Long lowPriceTimestamp240;


    public RangePrice(String symbol, Long highPriceTimestamp, BigDecimal highPrice, Long lowPriceTimestamp, BigDecimal lowPrice, BigDecimal averagePrice, BigDecimal highAveragePrice, BigDecimal lowAveragePrice, Integer rangeCount, Long highPriceTimestamp240, Long lowPriceTimestamp240) {
        this.symbol = symbol;
        this.highPriceTimestamp = highPriceTimestamp;
        this.highPrice = highPrice;
        this.lowPriceTimestamp = lowPriceTimestamp;
        this.lowPrice = lowPrice;
        this.averagePrice = averagePrice;
        this.highAveragePrice = highAveragePrice;
        this.lowAveragePrice = lowAveragePrice;
        this.rangeCount = rangeCount;
        this.highPriceTimestamp240 = highPriceTimestamp240;
        this.lowPriceTimestamp240 = lowPriceTimestamp240;
    }

    public RangePrice(String symbol, Long highPriceTimestamp, BigDecimal highPrice, Long lowPriceTimestamp, BigDecimal lowPrice, BigDecimal averagePrice, BigDecimal highAveragePrice, BigDecimal lowAveragePrice, Integer rangeCount) {
        this.symbol = symbol;
        this.highPriceTimestamp = highPriceTimestamp;
        this.highPrice = highPrice;
        this.lowPriceTimestamp = lowPriceTimestamp;
        this.lowPrice = lowPrice;
        this.averagePrice = averagePrice;
        this.highAveragePrice = highAveragePrice;
        this.lowAveragePrice = lowAveragePrice;
        this.rangeCount = rangeCount;

    }
}
