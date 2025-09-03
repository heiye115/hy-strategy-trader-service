package com.bitget.custom.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/***
 *  修改止盈止损计划委托
 * */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetModifyTpslOrderParam {

    /**
     * 订单ID
     **/
    private String orderId;
    /**
     * 自定义订单ID
     **/
    private String clientOid;
    /**
     * 保证金币种
     **/
    private String marginCoin;
    /**
     * 产品类型
     * usdt-futures USDT专业合约
     **/
    private String productType;
    /**
     * 交易币对
     **/
    private String symbol;
    /**
     * 触发价格
     **/
    private String triggerPrice;
    /**
     * 触发类型（fill_price （成交价格） mark_price（标记价格）
     **/
    private String triggerType;
    /**
     * 执行价格 （若为0或不填则代表市价执行。若填写大于0，为限价执行。当planType（止盈止损类型）为moving_plan（移动止盈止损）时则不填，固定为市价执行。）
     **/
    private String executePrice;
    /**
     * 下单数量
     * 对于仓位止盈止损订单，size必须为空"size":""
     **/
    private String size;

    /**
     * 回调幅度
     **/
    private String rangeRate;
}
