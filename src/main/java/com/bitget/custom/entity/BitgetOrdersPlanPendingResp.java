package com.bitget.custom.entity;

import lombok.*;

import java.util.List;

/**
 * 获取当前计划委托
 **/
@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetOrdersPlanPendingResp {

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
         * 委托类型
         * normal_plan 普通计划委托
         * track_plan 追踪委托
         * profit_plan 止盈计划（分批止盈止损）
         * loss_plan 止损计划（分批止盈止损）
         * pos_profit 仓位止盈
         * pos_loss 仓位止损
         * moving_plan 移动止盈止损
         */
        private String planType;

        /**
         * 交易对名称
         */
        private String symbol;

        /**
         * 委托数量
         */
        private String size;

        /**
         * 计划委托订单id
         */
        private String orderId;

        /**
         * 自定义计划委托订单id
         */
        private String clientOid;

        /**
         * 执行价格（非追踪委托时存在）
         */
        private String price;

        /**
         * 执行价格
         */
        private String executePrice;

        /**
         * 执行回调幅度（仅追踪委托时存在，范围1-10）
         */
        private String callbackRatio;

        /**
         * 触发价格（普通/追踪计划委托时存在）
         */
        private String triggerPrice;

        /**
         * 触发类型（fill_price/mark_price/index_price）
         */
        private String triggerType;

        /**
         * 订单状态，当前为 live（未触发）
         */
        private String planStatus;

        /**
         * 买卖方向：buy 买，sell 卖
         */
        private String side;

        /**
         * 持仓方向
         * long 双向持仓多头
         * short 双向持仓空头
         * net 单向持仓
         */
        private String posSide;

        /**
         * 保证金币种
         */
        private String marginCoin;

        /**
         * 保证金模式
         * isolated 逐仓
         * crossed 全仓
         */
        private String marginMode;

        /**
         * 订单来源
         * WEB、API、SYS、ANDROID、IOS
         */
        private String enterPointSource;

        /**
         * 交易方向：
         * open 开仓
         * close 平仓
         * sell_single 单独卖出
         * buy_single 单独买入
         */
        private String tradeSide;

        /**
         * 持仓模式：one_way_mode 单向，hedge_mode 双向
         */
        private String posMode;

        /**
         * 订单类型：limit 限价单，market 市价单
         */
        private String orderType;

        /**
         * 订单资源类型
         * normal 正常下单
         * market 市价单
         * profit_market 市价止盈单
         * loss_market 市价止损单
         * Trader_delegate 交易员带单
         * trader_profit 交易员止盈
         * trader_loss 交易员止损
         * trader_reverse 交易员带单反手
         * profit_limit 止盈限价
         * loss_limit 止损限价
         * delivery_close_short 空仓交割
         * pos_profit_limit 仓位止盈限价
         * pos_profit_market 仓位止盈市价
         * pos_loss_limit 仓位止损限价
         * pos_loss_market 仓位止损市价
         */
        private String orderSource;

        /**
         * 创建时间（毫秒）
         */
        private String cTime;

        /**
         * 最近更新时间（毫秒）
         */
        private String uTime;

        /**
         * 预设止盈值
         */
        private String stopSurplusExecutePrice;

        /**
         * 预设止盈触发价格
         */
        private String stopSurplusTriggerPrice;

        /**
         * 预设止盈触发类型
         * fill_price 成交价格
         * mark_price 标记价格
         * index_price 指数价格
         */
        private String stopSurplusTriggerType;

        /**
         * 预设止损值
         */
        private String stopLossExecutePrice;

        /**
         * 预设止损触发价格
         */
        private String stopLossTriggerPrice;

        /**
         * 预设止损触发类型
         * fill_price 成交价格
         * mark_price 标记价格
         * index_price 指数价格
         */
        private String stopLossTriggerType;

    }
}
