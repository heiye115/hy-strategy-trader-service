package com.hy.modules.contract.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * 马丁策略配置类
 **/
@Getter
@Setter
@ToString
@NoArgsConstructor
public class MartingaleStrategyConfig {

    /**
     * 是否启用策略 true表示启用，false表示禁用
     **/
    private Boolean enable;

    /**
     * 交易对 BTCUSDT、ETHUSDT、SOL 等
     */
    private String symbol;

    /**
     * 做多或做空 buy 买 做多 sell 卖 做空
     **/
    private String direction;

    /**
     * 数量小数位
     **/
    private Integer volumePlace;

    /**
     * 价格小数位
     **/
    private Integer pricePlace;

    /**
     * 杠杆倍数
     */
    private Integer leverage;

    /**
     * 跌/涨多少加仓(0.1%-50%)
     **/
    private Double addPositionPercentThreshold;

    /**
     * 单周期止盈目标(0.1%-1000%)
     **/
    private Double takeProfitPercentThreshold;

    /**
     * 初始开仓金额
     */
    private BigDecimal initialOpenAmount;

    /**
     * 最大加仓次数
     */
    private Integer maxOpenTimes;

    /**
     * 加仓金额倍数(0.10-10.00倍)
     */
    private Double addPositionAmountMultiple;

    /**
     * 加仓价差倍数(0.10-10.00倍)
     **/
    private Double addPositionPriceMultiple;

    public MartingaleStrategyConfig(Boolean enable, String symbol, String direction, Integer volumePlace, Integer pricePlace, Integer leverage, Double addPositionPercentThreshold, Double takeProfitPercentThreshold, BigDecimal initialOpenAmount, Integer maxOpenTimes, Double addPositionAmountMultiple, Double addPositionPriceMultiple) {
        this.enable = enable;
        this.symbol = symbol;
        this.direction = direction;
        this.volumePlace = volumePlace;
        this.pricePlace = pricePlace;
        this.leverage = leverage;
        this.addPositionPercentThreshold = addPositionPercentThreshold;
        this.takeProfitPercentThreshold = takeProfitPercentThreshold;
        this.initialOpenAmount = initialOpenAmount;
        this.maxOpenTimes = maxOpenTimes;
        this.addPositionAmountMultiple = addPositionAmountMultiple;
        this.addPositionPriceMultiple = addPositionPriceMultiple;
    }
}
