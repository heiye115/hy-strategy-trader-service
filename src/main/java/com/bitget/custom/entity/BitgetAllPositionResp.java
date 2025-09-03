package com.bitget.custom.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetAllPositionResp {

    /**
     * 保证金币种
     **/
    private String marginCoin;

    /**
     * 币对名称
     **/
    private String symbol;

    /**
     * 持仓方向
     **/
    private String holdSide;

    /**
     * 当前委托待成交的数量(交易币)
     **/
    private String openDelegateSize;

    /**
     * 保证金数量 (保证金币种)
     **/
    private String marginSize;

    /**
     * 仓位可用(计价币)
     **/
    private String available;

    /**
     * 仓位冻结(计价币)
     **/
    private String locked;

    /**
     * 仓位总数量(available + locked)
     **/
    private String total;

    /**
     * 杠杆倍数
     **/
    private String leverage;

    /**
     * 已实现盈亏
     **/
    private String achievedProfits;

    /**
     * 平均开仓价
     **/
    private String openPriceAvg;

    /**
     * 仓位盈亏平衡价
     **/
    private String breakEvenPrice;

    /**
     * 保证金模式
     **/
    private String marginMode;

    /**
     * 持仓模式
     * one_way_mode 单向持仓
     * hedge_mode 双向持仓
     **/
    private String posMode;

    /**
     * 未实现盈亏
     **/
    private String unrealizedPL;

    /**
     * 预估强平价
     **/
    private String liquidationPrice;

    /**
     * 维持保证金率
     **/
    private String keepMarginRate;

    /**
     * 标记价格
     **/
    private String markPrice;

    /**
     * 保证金比例
     **/
    private String marginRatio;

    /**
     * 资金费用，仓位存续期间，资金费用的累加值
     **/
    private String totalFee;

    /**
     * 最近更新时间 时间戳 毫秒
     * 集合中从最新时间开始降序。
     **/
    //@JsonProperty("cTime")
    private String cTime;

    private String deductedFee;
    private String grant;
    private String assetMode;
    private String autoMargin;
    private String takeProfit;
    private String stopLoss;
    private String takeProfitId;
    private String stopLossId;
    private String uTime;
}
