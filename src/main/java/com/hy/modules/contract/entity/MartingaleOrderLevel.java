package com.hy.modules.contract.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class MartingaleOrderLevel {

    /**
     * 第几单
     **/
    public Integer index;

    /**
     * 下单价格
     **/
    public BigDecimal price;

    /**
     * 本单保证金
     **/
    public BigDecimal margin;

    /**
     * 累计下跌/上涨百分比
     **/
    public BigDecimal cumulativeStep;

    /**
     * 本单数量
     **/
    public BigDecimal volume;

    public MartingaleOrderLevel(int index, BigDecimal price, BigDecimal margin, BigDecimal volume, BigDecimal cumulativeStep) {
        this.index = index;
        this.price = price;
        this.margin = margin;
        this.volume = volume;
        this.cumulativeStep = cumulativeStep;
    }
}
