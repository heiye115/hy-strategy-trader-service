package com.bitget.custom.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetOrderDetailResp {

    /**
     * 交易对名称
     **/
    private String symbol;

    /**
     * 委托数量
     **/
    private String size;

    /**
     * 订单ID
     **/
    private String orderId;

    /**
     * 自定义订单id
     **/
    private String clientOid;

    /**
     * 交易币成交数量
     **/
    private String baseVolume;

    /**
     * 手续费
     **/
    private String fee;

    /**
     * 成交均价
     **/
    private String priceAvg;

    /**
     * 委托价格
     **/
    private String price;

    /**
     * 订单状态
     * live 新建订单，orderbook中等待撮合
     * partially_filled 部分成交
     * filled 全部成交
     * canceled 已撤销
     **/
    private String state;

    /**
     * 开单方向
     * buy 买，sell 卖
     **/
    private String side;

    /**
     * 订单有效期
     * oc 无法立即成交的部分就撤销
     * fok 无法全部立即成交就撤
     * gtc 普通订单, 订单会一直有效，直到被成交或者取消
     * post_only 只做maker
     **/
    private String force;

    /**
     * 总盈亏
     **/
    private String totalProfits;

    /**
     * 持仓方向
     * long 双向持仓多头
     * short 双向持仓空头
     * net 单向持仓
     **/
    private String posSide;

    /**
     * 保证金币种，必须大写
     **/
    private String marginCoin;

    /**
     * 预设止盈值
     **/
    private String presetStopSurplusPrice;

    /**
     * 预设止损值
     **/
    private String presetStopLossPrice;

    /**
     * 计价币成交数量
     **/
    private String quoteVolume;

    /**
     * 交易类型
     * limit 限价
     * market 市价
     **/
    private String orderType;

    /**
     * 杠杆倍数
     **/
    private String leverage;

    /**
     * 保证金模式
     * isolated 逐仓
     * crossed 全仓
     **/
    private String marginMode;

    /**
     * 是否只减仓
     * YES 是
     * NO 否
     **/
    private String reduceOnly;

    /**
     * 订单来源
     * WEB 自Web端创建的订单
     * API 自API端创建的订单
     * SYS 系统托管订单, 通常由强制平仓逻辑生成
     * ANDROID 自Android端创建的订单
     * IOS 自IOS端创建的订单
     **/
    private String enterPointSource;

    /**
     * 交易方向
     * open 开（开平仓模式）
     * close 平（开平仓模式）
     * reduce_close_long 强制减多
     * reduce_close_short 强制减空
     * offset_close_long 轧差强制减多
     * offset_close_short 轧差强制减空
     * burst_close_long 爆仓平多
     * burst_close_short 爆仓平空
     * delivery_close_long 多头交割
     * delivery_close_short 空头交割
     **/
    private String tradeSide;

    /**
     * 持仓模式
     * one_way_mode 单向持仓
     * hedge_mode 双向持仓
     **/
    private String posMode;

    /**
     * 订单来源
     * normal 正常下单
     * market 市价单
     * profit_market 市价止盈单
     * loss_market 市价止损单
     * Trader_delegate 交易员带单下单
     * trader_profit 交易员止盈
     * trader_loss 交易员止损
     * reverse 反手订单
     * trader_reverse 交易员带单反手
     * profit_limit 止盈限价
     * loss_limit 止损限价
     * liquidation 爆仓单
     * delivery_close_long 多仓交割
     * delivery_close_short 空仓交割
     * pos_profit_limit 仓位止盈限价
     * pos_profit_market 仓位止盈市价
     * pos_loss_limit 仓位止损限价
     * pos_loss_market 仓位止损市价
     **/
    private String orderSource;

    /**
     * 订单来源
     **/
    private String newTradeSide;

    /**
     * 创建时间, ms
     **/
    @JsonProperty("cTime")
    private String cTime;

    /**
     * 更新时间, ms
     **/
    @JsonProperty("uTime")
    private String uTime;
}
