package com.hy.modules.cex.entity;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * 马丁交易下单
 **/
@Getter
@Setter
@ToString
@NoArgsConstructor
public class MartingalePlaceOrderParam {


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
     * 账户余额
     **/
    private BigDecimal accountBalance;
}
