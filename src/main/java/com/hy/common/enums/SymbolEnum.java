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
    XRPUSDT("XRPUSDT"),
    OPUSDT("OPUSDT"),
    ARBUSDT("ARBUSDT"),
    APTUSDT("APTUSDT"),
    SUIUSDT("SUIUSDT"),
    LDOUSDT("LDOUSDT"),
    RPLUSDT("RPLUSDT"),
    HYPEUSDT("HYPEUSDT"),
    ZECUSDT("ZECUSDT"),
    DOGEUSDT("DOGEUSDT"),
    BNBUSDT("BNBUSDT"),

    BTCUSDC("BTC"),
    ETHUSDC("ETH");

    private final String code;

    SymbolEnum(String code) {
        this.code = code;
    }
}
