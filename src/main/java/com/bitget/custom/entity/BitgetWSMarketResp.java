package com.bitget.custom.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetWSMarketResp {

    /**
     * 错误码
     **/
    private String code;

    /**
     * 错误消息
     **/
    private String msg;

    /**
     * 推送数据动作，增量推送数据还是全量推送数据
     **/
    private String action;

    /**
     * 订阅的频道
     **/
    private Arg arg;

    /**
     * 行情数据
     **/
    private List<MarketInfo> data;

    /**
     * 系统时间， 当前数据时间戳 Unix时间戳的毫秒数格式，如 1597026383085
     **/
    private Long ts;

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    public static class Arg {

        /**
         * 产品类型
         **/
        private String instType;

        /**
         * 频道名
         **/
        private String channel;

        /**
         * 产品ID
         * 如：ETHUSDT
         **/
        private String instId;
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    public static class MarketInfo {

        /**
         * 产品ID
         **/
        private String instId;

        /**
         * 最新成交价
         **/
        private String lastPr;

        /**
         * 买一价
         **/
        private String bidPr;

        /**
         * 卖一价
         **/
        private String askPr;

        /**
         * 买一量
         **/
        private String bidSz;

        /**
         * askSz
         **/
        private String askSz;

        /**
         * 开盘价 24小时。 开盘时间为24小时相对比，即：现在为2号19点，那么开盘时间对应为1号19点。
         **/
        private String open24h;

        /**
         * 24小时最高价
         **/
        private String high24h;

        /**
         * 24小时最低价
         **/
        private String low24h;

        /**
         * 24小时涨跌幅
         **/
        private String change24h;

        /**
         * 资金费率
         **/
        private String fundingRate;

        /**
         * 下次资金费率结算时间， Unix时间戳的毫秒数格式，如 1597026383085
         **/
        private String nextFundingTime;

        /**
         * 标记价格
         **/
        private String markPrice;

        /**
         * 指数价格
         **/
        private String indexPrice;

        /**
         * 持仓量
         **/
        private String holdingAmount;

        /**
         * 交易币交易量
         **/
        private String baseVolume;

        /**
         * 计价币交易额
         **/
        private String quoteVolume;

        /**
         * UTC 00:00时刻价格
         **/
        private String openUtc;

        /**
         * SymbolType：1->永续 2->交割
         **/
        private String symbolType;

        /**
         * 交易对
         **/
        private String symbol;

        /**
         * 交割合约交割价，symbolType=1（交割）时为0
         **/
        private String deliveryPrice;

        /**
         * 系统时间， 当前数据时间戳 Unix时间戳的毫秒数格式，如 1597026383085
         **/
        private String ts;

    }
}
