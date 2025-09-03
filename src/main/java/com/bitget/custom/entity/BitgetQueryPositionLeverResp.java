package com.bitget.custom.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetQueryPositionLeverResp {

    /**
     * 币对名
     **/
    private String symbol;

    /**
     * 阶梯档位
     **/
    private String level;

    /**
     * 价值下限
     **/
    private String startUnit;

    /**
     * 价值上限
     **/
    private String endUnit;

    /**
     * 杠杆倍数
     **/
    private String leverage;

    /**
     * 维持保证金率，持仓档位对应的数值，当仓位的保证金率小于维持保证金率时，将会触发强制减仓或爆仓
     **/
    private String keepMarginRate;
}
