package com.bitget.custom.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetHistoryPositionResp {

    /**
     * ID
     **/
    private String positionId;

    /**
     * 币对名称
     **/
    private String symbol;

    /**
     * 保证金币种
     **/
    private String marginCoin;

    /**
     * 持仓方向
     * long: 多仓
     * short: 空仓
     **/
    private String holdSide;

    /**
     * 持仓模式
     * one_way_mode: 单向持仓
     * hedge_mode: 双向持仓
     **/
    private String posMode;

    /**
     * 开仓均价
     **/
    private String openAvgPrice;

    /**
     * 平仓均价
     **/
    private String closeAvgPrice;

    /**
     * 保证金模式
     * isolated: 逐仓
     * crossed: 全仓
     **/
    private String marginMode;

    /**
     * 累计开仓数量
     **/
    private String openTotalPos;

    /**
     * 累计已平仓数量
     **/
    private String closeTotalPos;

    /**
     * 已实现盈亏
     **/
    private String pnl;

    /**
     * 净盈亏
     **/
    private String netProfit;

    /**
     * 累计资金费用
     **/
    private String totalFunding;

    /**
     * 仓位开仓总手续费
     **/
    private String openFee;

    /**
     * 仓位平仓总手续费
     **/
    private String closeFee;


    /**
     * 最近更新时间
     **/
    private String utime;

    /**
     * 创建时间 时间戳 毫秒
     * 集合中从最新时间开始降序。
     **/
    private String ctime;

}
