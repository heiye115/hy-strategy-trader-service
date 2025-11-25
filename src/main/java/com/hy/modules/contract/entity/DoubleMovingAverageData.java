package com.hy.modules.contract.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * 双均线数据
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class DoubleMovingAverageData {
    /**
     * MA21
     */
    private BigDecimal ma21;
    /**
     * MA55
     */
    private BigDecimal ma55;
    /**
     * MA144
     */
    private BigDecimal ma144;
    /**
     * EMA21
     */
    private BigDecimal ema21;
    /**
     * EMA55
     */
    private BigDecimal ema55;
    /**
     * EMA144
     */
    private BigDecimal ema144;

    public DoubleMovingAverageData(BigDecimal ma21, BigDecimal ma55, BigDecimal ma144, BigDecimal ema21, BigDecimal ema55, BigDecimal ema144) {
        this.ma21 = ma21;
        this.ma55 = ma55;
        this.ma144 = ma144;
        this.ema21 = ema21;
        this.ema55 = ema55;
        this.ema144 = ema144;
    }

    /**
     * 获取最高价
     **/
    public BigDecimal getMaxValue() {
        return ma21.max(ma55).max(ma144).max(ema21).max(ema55).max(ema144);
    }

    /**
     * 获取最低价
     **/
    public BigDecimal getMinValue() {
        return ma21.min(ma55).min(ma144).min(ema21).min(ema55).min(ema144);
    }

    /**
     * 获取144最高价
     **/
    public BigDecimal getMax144Value() {
        return ma144.max(ema144);
    }

    /**
     * 获取144最低价
     **/
    public BigDecimal getMin144Value() {
        return ma144.min(ema144);
    }

}
