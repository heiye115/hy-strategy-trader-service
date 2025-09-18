package com.hy.modules.contract.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 马丁策略配置校验类
 **/
@Getter
@Setter
@ToString
@NoArgsConstructor
public class MartingaleStrategyConfigVerify {

    /**
     * 马丁策略配置
     **/
    private MartingaleStrategyConfig martingaleStrategyConfig;

    /**
     * digestHex校验码
     **/
    private String digestHex;

    public MartingaleStrategyConfigVerify(MartingaleStrategyConfig martingaleStrategyConfig, String digestHex) {
        this.martingaleStrategyConfig = martingaleStrategyConfig;
        this.digestHex = digestHex;
    }
}
