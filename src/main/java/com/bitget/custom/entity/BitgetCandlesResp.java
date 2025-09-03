package com.bitget.custom.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetCandlesResp {

    /**
     * 时间戳（毫秒）
     */
    private Long timestamp;

    /**
     * 开盘价
     */
    private BigDecimal openPrice;

    /**
     * 最高价
     */
    private BigDecimal highPrice;

    /**
     * 最低价
     */
    private BigDecimal lowPrice;

    /**
     * 收盘价
     */
    private BigDecimal closePrice;

    /**
     * 成交量（基础币，如 BTC）
     */
    private BigDecimal baseVolume;

    /**
     * 成交量（USDT）
     */
    private BigDecimal usdtVolume;

    /**
     * 成交量（计价币，如 USDT）
     */
    private BigDecimal quoteVolume;

    public BitgetCandlesResp(List<String> datas) {
        if (datas.size() < 8) {
            throw new IllegalArgumentException("Kline data format error");
        }
        this.timestamp = Long.parseLong(datas.get(0));
        this.openPrice = new BigDecimal(datas.get(1));
        this.highPrice = new BigDecimal(datas.get(2));
        this.lowPrice = new BigDecimal(datas.get(3));
        this.closePrice = new BigDecimal(datas.get(4));
        this.baseVolume = new BigDecimal(datas.get(5));
        this.usdtVolume = new BigDecimal(datas.get(6));
        this.quoteVolume = new BigDecimal(datas.get(7));
    }
}
