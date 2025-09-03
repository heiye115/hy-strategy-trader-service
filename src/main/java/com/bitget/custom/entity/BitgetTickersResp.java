package com.bitget.custom.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetTickersResp {

    /**
     * 交易对名称
     **/
    private String symbol;
    /**
     * 24小时最高价
     **/
    private String high24h;
    /**
     * 24小时开盘价
     **/
    private String open;
    /**
     * 最新成交价
     **/
    private String lastPr;
    /**
     * 24小时最低价
     **/
    private String low24h;
    /**
     * 计价币成交额
     **/
    private String quoteVolume;
    /**
     * 基础币成交额
     **/
    private String baseVolume;
    /**
     * USDT成交额
     **/
    private String usdtVolume;
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
     * 卖一量
     **/
    private String askSz;
    /**
     * 零时区 开盘价
     **/
    private String openUtc;
    /**
     * 当前时间。Unix毫秒时间戳，例如1690196141868
     **/
    private Long ts;
    /**
     * UTC0时涨跌幅, 0.01表示1%
     **/
    private String changeUtc24h;
    /**
     * 24小时涨跌幅, 0.01表示1%
     **/
    private String change24h;

}
