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
public class DualMovingAverageStrategyConfig {

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
     * 最小差异百分比（例如 0.4 表示 0.4%）
     **/
    private Double minPercentThreshold;

    /**
     * 杠杆倍数
     */
    private Integer leverage;

    /**
     * 开仓金额
     */
    private BigDecimal openAmount;

    /**
     * 第1次止盈涨跌幅百分比
     */
    private Double takeProfitPercent1;

    /**
     * 第1次止盈仓位百分比
     */
    private Double takeProfitPositionPercent1;

    /**
     * 第2次止盈涨跌幅百分比
     */
    private Double takeProfitPercent2;

    /**
     * 第2次止盈仓位百分比
     */
    private Double takeProfitPositionPercent2;

    public DualMovingAverageStrategyConfig(Boolean enable, String symbol,
                                           Double minPercentThreshold,
                                           Integer leverage,
                                           BigDecimal openAmount,
                                           Integer volumePlace,
                                           Integer pricePlace,
                                           String timeFrame,
                                           Double takeProfitPercent1,
                                           Double takeProfitPositionPercent1,
                                           Double takeProfitPercent2,
                                           Double takeProfitPositionPercent2) {
        this.enable = enable;
        this.symbol = symbol;
        this.minPercentThreshold = minPercentThreshold;
        this.leverage = leverage;
        this.openAmount = openAmount;
        this.timeFrame = timeFrame;
        this.volumePlace = volumePlace;
        this.pricePlace = pricePlace;
        this.takeProfitPercent1 = takeProfitPercent1;
        this.takeProfitPositionPercent1 = takeProfitPositionPercent1;
        this.takeProfitPercent2 = takeProfitPercent2;
        this.takeProfitPositionPercent2 = takeProfitPositionPercent2;
    }
}
