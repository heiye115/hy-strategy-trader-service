package com.bitget.custom.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetSetLeverageResp {


    /**
     * 币对名称
     **/
    private String symbol;

    /**
     * 保证金币种
     **/
    private String marginCoin;

    /**
     * 多仓杠杆
     **/
    private String longLeverage;

    /**
     * 空仓杠杆
     **/
    private String shortLeverage;

    /**
     * 全仓杠杆
     **/
    private String crossMarginLeverage;
    
    /**
     * 保证金模式。 isolated逐仓模式；crossed 全仓模式
     **/
    private String marginMode;
}
