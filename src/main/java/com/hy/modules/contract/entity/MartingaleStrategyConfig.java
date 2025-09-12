package com.hy.modules.contract.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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
     * 初始开仓金额
     */
    private Double initialOpenAmount;

    /**
     * 最大开仓次数
     */
    private Integer maxOpenTimes;

    /**
     * 每次加仓的百分比
     */
    private Double addPositionPercent;

    /**
     * 止盈百分比
     */
    private Double takeProfitPercent;
}
