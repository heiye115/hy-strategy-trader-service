package com.bitget.custom.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetAccountsResp {

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
    private String unrealizedPL;

    /**
     * 体验金
     **/
    private String coupon;


    private String grant;

    /**
     * 联合保证金
     **/
    private String unionTotalMargin;

    /**
     * 联合保证金模式下的可用
     **/
    private String unionAvailable;

    /**
     * 联合保证金模式下的维持保证金
     **/
    private String unionMm;

    /**
     * 联合保证金
     * union 联合保证金
     * single 单币种保证金
     **/
    private String assetMode;

    /**
     * 逐仓占用保证金
     **/
    private String isolatedMargin;

    /**
     * 全仓占用保证金
     **/
    private String crossedMargin;
}
