package com.hy.common.enums;

import lombok.Getter;

/**
 * 交易对枚举类
 **/
@Getter
public enum SymbolEnum {


    BTCUSDT("BTCUSDT"),
    ETHUSDT("ETHUSDT"),
    SOLUSDT("SOLUSDT"),
    XRPUSDT("XRPUSDT");

    private final String code;

    SymbolEnum(String code) {
        this.code = code;
    }
}
