package com.bitget.custom.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetAccountResp {

    /**
     * 保证金币种
     **/
    private String marginCoin;

    /**
     * 锁定数量(保证金币种)
     **/
    private String locked;

    /**
     * 账户可用数量
     **/
    private String available;

    /**
     * 全仓最大可用来开仓余额(保证金币种)
     **/
    private String crossedMaxAvailable;

    /**
     * 逐仓最大可用来开仓余额(保证金币种)
     **/
    private String isolatedMaxAvailable;

    /**
     * 最大可转出
     **/
    private String maxTransferOut;

    /**
     * 账户权益(保证金币种)，
     * 包含未实现盈亏（根据mark price计算）
     **/
    private String accountEquity;

    /**
     * 折算USDT账户权益
     **/
    private String usdtEquity;

    /**
     * 折算BTC账户权益
     **/
    private String btcEquity;

    /**
     * 全仓时风险率
     **/
    private String crossedRiskRate;

    /**
     * 未实现盈亏
     **/
    private String unrealizedPl;

    /**
     * 体验金
     **/
    private String coupon;

    /**
     * 全仓时杠杆倍数
     **/
    private Long crossedMarginLeverage;

    /**
     * 逐仓时多头杠杆数
     **/
    private Long isolatedLongLever;

    /**
     * 逐仓时空头杠杆数
     **/
    private Long isolatedShortLever;

    /**
     * 保证金模式。
     * isolated逐仓模式；
     * crossed 全仓模式
     **/
    private String marginMode;

    /**
     * 持仓模式
     * one_way_mode 单向持仓
     * hedge_mode 双向持仓
     **/
    private String posMode;

    /**
     * 全仓未实现盈亏
     **/
    private String crossedUnrealizedPl;

    /**
     * 逐仓未实现盈亏
     **/
    private String isolatedUnrealizedPL;
}
