package com.hy.modules.cex.entity;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * 区间交易订单信息
 **/
@Getter
@Setter
@ToString
@NoArgsConstructor
public class RangePriceOrder {

    /**
     * 自定义订单id
     **/
    private String clientOid;

    /**
     * 订单id
     **/
    private String orderId;

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
    private String presetStopLossPrice;

    /**
     * 初始开仓金额
     **/
    private BigDecimal initialOpenAmount;

    /**
     * 创建时间
     **/
    private String createTime;
}
