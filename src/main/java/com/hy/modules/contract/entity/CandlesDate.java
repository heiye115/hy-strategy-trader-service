package com.hy.modules.contract.entity;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * K线时间区间
 **/
@Getter
@Setter
@ToString
@NoArgsConstructor
public class CandlesDate {

    /**
     * 开始时间
     **/
    private Long startTime;

    /**
     * 结束时间
     **/
    private Long endTime;

    public CandlesDate(Long startTime, Long endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

}
