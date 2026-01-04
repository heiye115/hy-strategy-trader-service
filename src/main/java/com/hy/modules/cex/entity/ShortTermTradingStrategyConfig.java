package com.hy.modules.cex.entity;

import com.hy.common.enums.BitgetEnum;
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
public class ShortTermTradingStrategyConfig {

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
    private BitgetEnum granularity;

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

    /**
     * 止盈盈亏比 如:1.5表示1:1.5
     */
    private Double takeProfitProfitLossRatio;


    public ShortTermTradingStrategyConfig(Boolean enable, String symbol,
                                          Integer leverage,
                                          BigDecimal openAmount,
                                          Integer volumePlace,
                                          Integer pricePlace,
                                          BitgetEnum granularity,
                                          Double takeProfitProfitLossRatio) {
        this.enable = enable;
        this.symbol = symbol;
        this.leverage = leverage;
        this.openAmount = openAmount;
        this.granularity = granularity;
        this.volumePlace = volumePlace;
        this.pricePlace = pricePlace;
        this.takeProfitProfitLossRatio = takeProfitProfitLossRatio;
    }

}
