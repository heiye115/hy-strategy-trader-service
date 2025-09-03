package com.bitget.custom.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetSetPositionModeResp {

    /**
     * 持仓模式
     * one_way_mode 单向持仓
     * hedge_mode 双向持仓
     **/
    private String posMode;


}
