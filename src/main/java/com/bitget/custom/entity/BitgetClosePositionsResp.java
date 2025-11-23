package com.bitget.custom.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * 一键市价平仓 结果集
 **/
@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetClosePositionsResp {

    /**
     * 平仓成功单集合。
     **/
    private List<SuccessList> successList;

    /**
     * 平仓失败单集合。
     **/
    private List<FailureList> failureList;

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    public static class SuccessList {
        /**
         * 订单id
         **/
        private String orderId;

        /**
         * 自定义订单id
         **/
        private String clientOid;

        /**
         * 交易对
         **/
        private String symbol;
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    public static class FailureList {
        /**
         * 订单id
         **/
        private String orderId;

        /**
         * 自定义订单id
         **/
        private String clientOid;
        /**
         * 交易对
         **/
        private String symbol;
        /**
         * 失败原因
         **/
        private String errorMsg;
        /**
         * 错误码
         **/
        private String errorCode;
    }

}
