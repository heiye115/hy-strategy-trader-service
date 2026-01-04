package com.hy.modules.dex.entity;

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
public class MovingAverageStrategyConfig {

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
     * 交易所最大杠杆倍数
     */
    private Integer maxLeverage;

    /**
     * 开仓金额
     */
    private BigDecimal openAmount;

    /**
     * 当前价与最高均线价或最低均线价偏离百分比 例如: 1.0% 表示当前价距离最高或最低均线上涨或下跌超过百分之1
     * 用于动态止盈
     **/
    private BigDecimal deviationFromMA;

    public MovingAverageStrategyConfig(Boolean enable, String symbol, String timeFrame, Integer volumePlace, Integer pricePlace, Integer maxLeverage, BigDecimal openAmount, BigDecimal deviationFromMA) {
        this.enable = enable;
        this.symbol = symbol;
        this.timeFrame = timeFrame;
        this.volumePlace = volumePlace;
        this.pricePlace = pricePlace;
        this.maxLeverage = maxLeverage;
        this.openAmount = openAmount;
        this.deviationFromMA = deviationFromMA;
    }
}
