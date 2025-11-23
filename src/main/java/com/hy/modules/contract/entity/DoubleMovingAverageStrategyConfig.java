package com.hy.modules.contract.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * 策略配置类
 **/
@Getter
@Setter
@ToString
@NoArgsConstructor
public class DoubleMovingAverageStrategyConfig {

    /**
     * 是否启用策略 true表示启用，false表示禁用
     **/
    private Boolean enable;

    /**
     * 交易对 BTCUSDT、ETHUSDT、SOL 等
     */
    private String symbol;

    /**
     * 交易周期 如1h周期 4h周期
     **/
    private String timeFrame;

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
     * 开仓金额
     */
    private BigDecimal openAmount;

    public DoubleMovingAverageStrategyConfig(Boolean enable, String symbol, String timeFrame, Integer volumePlace, Integer pricePlace, Integer leverage, BigDecimal openAmount) {
        this.enable = enable;
        this.symbol = symbol;
        this.timeFrame = timeFrame;
        this.volumePlace = volumePlace;
        this.pricePlace = pricePlace;
        this.leverage = leverage;
        this.openAmount = openAmount;
    }
}
