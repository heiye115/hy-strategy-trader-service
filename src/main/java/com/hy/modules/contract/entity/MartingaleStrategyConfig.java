package com.hy.modules.contract.entity;

import com.hy.common.enums.Direction;
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
     * 做多或做空 LONG, SHORT
     **/
    private Direction direction;

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
     * 最大投入金额
     */
    private BigDecimal maxInvestAmount;

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

    /**
     * 最小下单数量
     **/
    private String minTradeSize;

    /**
     * 开启复利模式
     **/
    private Boolean compoundEnable;

    public MartingaleStrategyConfig(Boolean enable, String symbol, Direction direction, Integer volumePlace, Integer pricePlace, Integer leverage, Double addPositionPercentThreshold, Double takeProfitPercentThreshold, BigDecimal maxInvestAmount, Integer maxOpenTimes, Double addPositionAmountMultiple, Double addPositionPriceMultiple, String minTradeSize, Boolean compoundEnable) {
        this.enable = enable;
        this.symbol = symbol;
        this.direction = direction;
        this.volumePlace = volumePlace;
        this.pricePlace = pricePlace;
        this.leverage = leverage;
        this.addPositionPercentThreshold = addPositionPercentThreshold;
        this.takeProfitPercentThreshold = takeProfitPercentThreshold;
        this.maxInvestAmount = maxInvestAmount;
        this.maxOpenTimes = maxOpenTimes;
        this.addPositionAmountMultiple = addPositionAmountMultiple;
        this.addPositionPriceMultiple = addPositionPriceMultiple;
        this.minTradeSize = minTradeSize;
        this.compoundEnable = compoundEnable;
    }
}
