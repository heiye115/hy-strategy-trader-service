package com.hy.common.enums;

import lombok.Getter;

import java.time.Duration;

/**
 * 业务消息枚举类
 **/
@Getter
public enum BitgetEnum {


    M1("1m", Duration.ofMinutes(1)),
    M5("5m", Duration.ofMinutes(5)),
    M15("15m", Duration.ofMinutes(15)),
    M30("30m", Duration.ofMinutes(30)),
    H1("1H", Duration.ofHours(1)),
    H4("4H", Duration.ofHours(4));

    private final String code;

    private final Duration duration;

    BitgetEnum(String code, Duration duration) {
        this.code = code;
        this.duration = duration;
    }

    /***
     * 根据code获取枚举
     * @param code 枚举code
     * @return BitgetEnum
     **/
    public static BitgetEnum getByCode(String code) {
        for (BitgetEnum bitgetEnum : BitgetEnum.values()) {
            if (bitgetEnum.getCode().equals(code)) {
                return bitgetEnum;
            }
        }
        return null;
    }

}
