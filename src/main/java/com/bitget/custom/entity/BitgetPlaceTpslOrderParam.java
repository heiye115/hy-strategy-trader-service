package com.bitget.custom.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/***止盈止损计划委托下单参数*/
@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetPlaceTpslOrderParam {

    /**
     * 保证金币种(大写)
     **/
    private String marginCoin;
    /**
     * 产品类型 usdt-futures USDT专业合约
     **/
    private String productType;
    /**
     * 交易币对
     **/
    private String symbol;
    /**
     * 止盈止损类型
     * profit_plan：止盈计划
     * loss_plan：止损计划
     * moving_plan：移动止盈止损
     * pos_profit：仓位止盈
     * pos_loss：仓位止损
     **/
    private String planType;
    /**
     * 触发价格
     **/
    private String triggerPrice;
    /**
     * 触发类型
     * fill_price：市场价格
     * mark_price：标记价格
     **/
    private String triggerType;
    /**
     * 执行价格
     * 不传或者传0则市价执行。大于0为限价执行
     * planType为moving_plan时不允许传此参数，固定为市价执行
     **/
    private String executePrice;

    /**
     * 双向持仓：long：多仓，short：空仓;
     * <p>
     * 单向持仓：buy：多仓，sell：空仓
     **/
    private String holdSide;

    /**
     * 下单数量(基础币)
     * planType为profit_plan、loss_plan、moving_plan时必填（大于0）
     * planType为pos_profit、pos_loss时非必填
     **/
    private String size;
    /**
     * 回调幅度
     * planType为moving_plan时必填
     **/
    private String rangeRate;
    /**
     * 自定义订单id
     **/
    private String clientOid;
    /**
     * STP模式， default none
     * none 不设置STP
     * cancel_taker 取消taker单
     * cancel_maker 取消maker单
     * cancel_both 两者都取消
     **/
    private String stpMode;
}
