package com.bitget.custom.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * 批量撤单 参数
 **/
@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetBatchCancelOrdersParam {

    /**
     * 订单id集合。最大长度：50
     **/
    private List<Order> orderIdList;

    /**
     * 交易对 如:ethusdt
     * 当传入orderIdList参数时，此参数为必传
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
     * 保证金币种 必须大写
     **/
    private String marginCoin;

    public BitgetBatchCancelOrdersParam(String symbol, String productType, String marginCoin) {
        this.symbol = symbol;
        this.productType = productType;
        this.marginCoin = marginCoin;
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    public static class Order {
        /**
         * 自定义订单id
         **/
        private String clientOid;

        /**
         * 订单id
         **/
        private String orderId;

        public Order(String clientOid, String orderId) {
            this.clientOid = clientOid;
            this.orderId = orderId;
        }
    }
}
