package com.hy.modules.contract.entity;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * 区间交易下单
 **/
@Getter
@Setter
@ToString
@NoArgsConstructor
public class RangePricePlaceOrderParam {


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
     * 交易类型(仅限双向持仓)
     * 双向持仓模式下必填，单向持仓时不要填，否则会报错
     * open: 开仓
     * close: 平仓
     **/
    private String tradeSide;

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
     * 预设止损价
     **/
    private BigDecimal presetStopLossPrice;

    /**
     * 预设止盈价1
     **/
    private BigDecimal presetStopSurplusPrice1;

    /**
     * 预设止盈价2
     **/
    private BigDecimal presetStopSurplusPrice2;

    /**
     * 预设止盈价3
     **/
    private BigDecimal presetStopSurplusPrice3;

}
