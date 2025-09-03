package com.hy.modules.contract.entity;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * 双均线交易止盈止损计划委托下单
 **/
@Getter
@Setter
@ToString
@NoArgsConstructor
public class DualMovingAveragePlaceTpslOrder {

    /**
     * 币对名称
     **/
    private String symbol;

    /**
     * 持仓方向
     **/
    private String holdSide;

    /**
     * 止盈价1
     **/
    private BigDecimal takeProfitPrice1;

    /**
     * 止盈仓位1
     **/
    private BigDecimal takeProfitSize1;

    /**
     * 止盈价2
     **/
    private BigDecimal takeProfitPrice2;

    /**
     * 止盈仓位2
     **/
    private BigDecimal takeProfitSize2;

    /**
     * 止损价
     **/
    private BigDecimal stopLossPrice;
    
}
