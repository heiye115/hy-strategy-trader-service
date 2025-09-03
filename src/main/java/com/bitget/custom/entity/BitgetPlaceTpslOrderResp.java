package com.bitget.custom.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetPlaceTpslOrderResp {

    /**
     * 自定义订单id
     **/
    private String clientOid;

    /**
     * 订单id
     **/
    private String orderId;

}
