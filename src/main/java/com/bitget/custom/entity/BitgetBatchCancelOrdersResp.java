package com.bitget.custom.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * 批量撤单 返回结果
 **/
@Getter
@Setter
@ToString
@NoArgsConstructor
public class BitgetBatchCancelOrdersResp {

    private List<Success> successList;

    private List<Failure> failureList;

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    public static class Success {
        /**
         * 自定义订单id
         **/
        private String clientOid;

        /**
         * 订单id
         **/
        private String orderId;
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    public static class Failure {
        /**
         * 自定义订单id
         **/
        private String clientOid;

        /**
         * 订单id
         **/
        private String orderId;

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
