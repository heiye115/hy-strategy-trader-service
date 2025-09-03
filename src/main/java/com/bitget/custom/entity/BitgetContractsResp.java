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
public class BitgetContractsResp {

    /**
     * 产品名称，如 ETHUSDT
     */
    private String symbol;

    /**
     * 基础币，如 ETHUSDT 中的 ETH
     */
    private String baseCoin;

    /**
     * 计价币，如 ETHUSDT 中的 USDT
     */
    private String quoteCoin;

    /**
     * 买价限价比例
     */
    private String buyLimitPriceRatio;

    /**
     * 卖价限价比例
     */
    private String sellLimitPriceRatio;

    /**
     * 手续费上浮比例
     */
    private String feeRateUpRatio;

    /**
     * maker 手续费率
     */
    private String makerFeeRate;

    /**
     * taker 手续费率
     */
    private String takerFeeRate;

    /**
     * 开仓成本上浮比例
     */
    private String openCostUpRatio;

    /**
     * 支持保证金币种列表
     */
    private List<String> supportMarginCoins;

    /**
     * 最小开单数量（基础币）
     */
    private String minTradeNum;

    /**
     * 价格步长
     */
    private String priceEndStep;

    /**
     * 数量小数位
     */
    private String volumePlace;

    /**
     * 价格小数位
     */
    private String pricePlace;

    /**
     * 数量乘数：下单数量要大于 minTradeNum 且满足 sizeMultiplier 的倍数
     */
    private String sizeMultiplier;

    /**
     * 合约类型：perpetual（永续），delivery（交割）
     */
    private String symbolType;

    /**
     * 最小 USDT 交易额
     */
    private String minTradeUSDT;

    /**
     * 单个 symbol 最大持有订单数
     */
    private String maxSymbolOrderNum;

    /**
     * 产品类型维度下最大持有订单数
     */
    private String maxProductOrderNum;

    /**
     * 最大持有仓位数量
     */
    private String maxPositionNum;

    /**
     * 交易对状态：
     * listed：上架
     * normal：正常/开盘
     * maintain：禁止交易(禁止开平仓)
     * limit_open：限制下单(可平仓)
     * restrictedAPI：API 限制下单
     * off：下架
     */
    private String symbolStatus;

    /**
     * 下架时间，'-1' 表示正常
     */
    private String offTime;

    /**
     * 可开仓时间，'-1' 表示正常；否则表示维护前的时间
     */
    private String limitOpenTime;

    /**
     * 交割时间
     */
    private String deliveryTime;

    /**
     * 交割开始时间
     */
    private String deliveryStartTime;

    /**
     * 交割周期：
     * this_quarter：当季
     * next_quarter：次季
     */
    private String deliveryPeriod;

    /**
     * 上架时间
     */
    private String launchTime;

    /**
     * 资金费结算周期（如每小时/每8小时）
     */
    private String fundInterval;

    /**
     * 最小杠杆倍数
     */
    private String minLever;

    /**
     * 最大杠杆倍数
     */
    private String maxLever;

    /**
     * 持仓限制
     */
    private String posLimit;

    /**
     * 当前处于维护状态或即将维护时的时间
     */
    private String maintainTime;

    /**
     * 单笔市价单最大下单数量（基础币）
     */
    private String maxMarketOrderQty;

    /**
     * 单笔限价单最大下单数量（基础币）
     */
    private String maxOrderQty;

}