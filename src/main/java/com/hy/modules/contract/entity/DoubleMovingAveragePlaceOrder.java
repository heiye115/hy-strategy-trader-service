package com.hy.modules.contract.entity;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * 双均线交易下单
 **/
@Getter
@Setter
@ToString
@NoArgsConstructor
public class DoubleMovingAveragePlaceOrder {


    /**
     * 自定义订单id
     **/
    private String clientOid;

    /**
     * 交易对
     **/
    private String symbol;

    /**
     * 下单数量(基础币)
     * 数量小数位可以通过获取合约信息 接口获取
     **/
    private String size;

    /**
     * 交易方向
     * buy: 单向持仓时代表买入，双向持仓时代表多头方向
     * sell: 单向持仓时代表卖出，双向持仓时代表空头方向
     **/
    private String side;

    /**
     * 下单价格。
     * orderType为limit时必填
     * 价格小数位可以通过获取合约信息 接口获取
     **/
    private BigDecimal price;


    /**
     * 订单类型
     * limit: 限价单，
     * market: 市价单
     **/
    private String orderType;

    /**
     * 仓位模式
     * isolated: 逐仓
     * crossed: 全仓
     **/
    private String marginMode;

    /**
     * 预设止损值
     * 为空则默认不设止损。
     **/
    private String stopLossPrice;

    /**
     * 预设止盈价
     * 为空则默认不设止盈。
     */
    private String takeProfitPrice;

    /**
     * 止盈数量
     **/
    private String takeProfitSize;

    /**
     * 账户余额
     **/
    private BigDecimal accountBalance;

    /**
     * 杠杆倍数
     **/
    private Integer leverage;
}
