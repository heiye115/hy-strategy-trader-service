package com.bitget.custom.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * 批量下单 参数
 **/
@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetBatchPlaceOrderParam {
    /**
     * 交易对 必填
     **/
    private String symbol;

    /**
     * 产品类型 必填
     * USDT-FUTURES U本位合约
     * COIN-FUTURES 币本位合约
     * USDC-FUTURES USDC合约
     **/
    private String productType;

    /**
     * 保证金币种 必须大写 必填
     **/
    private String marginCoin;


    /**
     * 仓位模式 必填
     * isolated：逐仓
     * crossed：全仓
     **/
    private String marginMode;

    /**
     * 下单集合。最大订单数(列表长度)：50  必填
     **/
    private List<Order> orderList;

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    public static class Order {
        /**
         * 下单数量 必填
         **/
        private String size;

        /**
         * 下单价格。 orderType为limit时必填  可选
         **/
        private String price;

        /**
         * 下单方向 必填
         * buy：买
         * sell：卖
         **/
        private String side;

        /**
         * 交易方向 开平仓双向持仓模式下必填，单向持仓时不要填，否则会报错  可选
         * open: 开
         * close: 平
         **/
        private String tradeSide;

        /**
         * 订单类型 必填
         * limit: 限价单，
         * market: 市价单
         **/
        private String orderType;

        /**
         * 订单有效期 orderType为limit时必填，若省略则默认为gtc 可选
         * ioc: 无法立即成交的部分就撤销
         * fok: 无法全部立即成交就撤销
         * gtc: 普通订单, 订单会一直有效，直到被成交或者取消
         * post_only: 只做maker
         **/
        private String force;

        /**
         * 自定义订单id 可选
         **/
        private String clientOid;

        /**
         * 是否只减仓 YES，NO 默认为NO 仅适用于单向持仓模式。 可选
         **/
        private String reduceOnly;

        /**
         * 止盈值 为空则默认不设止盈。 可选
         **/
        private String presetStopSurplusPrice;

        /**
         * 止损值 为空则默认不设止损。 可选
         **/
        private String presetStopLossPrice;

        /**
         * STP模式， default none 可选
         * none 不设置STP
         * cancel_taker 取消taker单
         * cancel_maker 取消maker单
         * cancel_both 两者都取消
         **/
        private String stpMode;

    }


}
