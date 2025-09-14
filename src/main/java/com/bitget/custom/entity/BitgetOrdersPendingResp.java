package com.bitget.custom.entity;

import lombok.*;

import java.util.List;

/**
 * 查询当前委托 结果集
 **/
@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetOrdersPendingResp {

    /**
     * 委托集合
     */
    private List<EntrustedOrder> entrustedList;

    /**
     * 最后的订单ID（用于分页）
     */
    private String endId;

    @Data
    public static class EntrustedOrder {

        /**
         * 交易对名称
         */
        private String symbol;

        /**
         * 委托数量
         **/
        private String size;

        /**
         * 订单id
         **/
        private String orderId;

        /**
         * 自定义id
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
         * 委托价格
         **/
        private String price;

        /**
         * 订单状态
         * live: 等待成交（尚未有任何成交）
         * partially_filled: 部分成交
         **/
        private String status;

        /**
         * 开单方向
         * buy 买，sell 卖
         **/
        private String side;

        /**
         * 订单有效期
         * ioc 无法立即成交的部分就撤销
         * fok 无法全部立即成交就撤销
         * gtc 普通订单, 订单会一直有效，直到被成交或者取消
         * post_only 只做maker
         **/
        private String force;

        /**
         * 总盈亏
         * status为live时为空。
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
         * 保证金币种
         **/
        private String marginCoin;

        /**
         * 计价币成交数量
         **/
        private String quoteVolume;

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
         * reduce_close_long 双向持仓强制减多
         * reduce_close_short 双向持仓强制减空
         * offset_close_long 双向持仓轧差强制减多
         * offset_close_short 双向持仓轧差强制减空
         * burst_close_long 双向持仓爆仓平多
         * burst_close_short 双向持仓爆仓平空
         * delivery_close_long 双向持仓多头交割
         * delivery_close_short 双向持仓空头交割
         * dte_sys_adl_close_long 双向持仓ADL减多仓
         * dte_sys_adl_close_short 双向持仓ADL减空仓
         * buy_single 单向持仓买
         * sell_single 单向持仓卖
         * reduce_buy_single 单向持仓强制减仓买
         * reduce_sell_single 单向持仓强制减仓卖
         * burst_buy_single 单向持仓爆仓买
         * burst_sell_single 单向持仓爆仓卖
         * delivery_sell_single 单向持仓交割卖
         * delivery_buy_single 单向持仓交割买
         * dte_sys_adl_buy_in_single_side_mode 单向持仓ADL减仓买
         * dte_sys_adl_sell_in_single_side_mode 单向持仓ADL减仓卖
         **/
        private String tradeSide;

        /**
         * 持仓模式
         * one_way_mode 单向持仓
         * hedge_mode 双向持仓
         **/
        private String posMode;

        /**
         * 订单类型
         * limit 限价单
         * market 市价单
         **/
        private String orderType;

        /**
         * 订单资源
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
         * profit_chase 止盈追价委托
         * loss_chase 止损追价委托
         * follower_delegate 跟单委托
         * reduce_offset 减仓扎差委托
         * market_risk 最优价风险处理
         * plan_limit 限价计划委托
         * plan_market 最优价计划委托
         * pos_loss_limit 仓位止损限价
         * strategy_positive 策略-正向网格
         * strategy_reverse 策略-反向网格
         * strategy_unlimited 无限策略
         * move_limit 限价移动止盈止损
         * move_market 最优价移动止盈止损
         * tracking_limit 限价追踪委托
         * tracking_market 最优价追踪委托
         * strategy_dca_positive DCA策略-正向
         * strategy_dca_reverse DCA策略-反向
         * strategy_oco_limit 策略-OCO限价单
         * strategy_oco_trigger 策略-OCO触发单
         * modify_order_limit 限价修改订单
         * strategy_regular_buy 策略-定投策略买
         * strategy_grid_middle 策略-中性网格
         **/
        private String orderSource;

        /**
         * 是否只减仓
         **/
        private String reduceOnly;

        /**
         * 创建时间
         **/
        private String cTime;

        /**
         * 最近更新时间
         **/
        private String uTime;
    }
}
