package com.hy.common.service;

import com.bitget.custom.entity.*;
import com.bitget.openapi.common.client.BitgetRestClient;
import com.bitget.openapi.common.domain.ClientParameter;
import com.bitget.openapi.common.enums.SignTypeEnum;
import com.bitget.openapi.common.enums.SupportedLocaleEnum;
import com.bitget.openapi.dto.request.ws.SubscribeReq;
import com.bitget.openapi.dto.response.ResponseResult;
import com.bitget.openapi.ws.BitgetWsClient;
import com.bitget.openapi.ws.BitgetWsHandle;
import com.bitget.openapi.ws.SubscriptionListener;
import com.google.common.collect.Maps;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.hy.common.constants.BitgetConstant.BG_PRODUCT_TYPE_USDT_FUTURES;
import static com.hy.common.constants.BitgetConstant.DEFAULT_CURRENCY_USDT;
import static com.hy.common.utils.json.JsonUtil.*;

/**
 * bitget自定义服务类
 **/
@Slf4j
@Service
public class BitgetCustomService {

    @Value("${bitget.api-Key}")
    private String apiKey;

    @Value("${bitget.secret-key}")
    private String secretKey;

    @Value("${bitget.passphrase}")
    private String passphrase;

    @Value("${bitget.base-url}")
    private String baseUrl;

    @Value("${bitget.ws-public-url}")
    private String wsPublicUrl;

    @Value("${bitget.ws-private-url}")
    private String wsPrivateUrl;

    private BitgetRestClient bitgetRestClient;

    private BitgetRestClient bitgetRestClient() {
        ClientParameter parameter = ClientParameter.builder()
                .apiKey(apiKey)
                .secretKey(secretKey) // 如果是RSA类型则设置为RSA私钥
                .passphrase(passphrase)
                .baseUrl(baseUrl)
                .signType(SignTypeEnum.RSA) // 如果你的apikey是RSA类型则主动设置签名类型为RSA
                .locale(SupportedLocaleEnum.ZH_CN.getName()).build();
        return BitgetRestClient.builder().configuration(parameter).build();
    }

    @PostConstruct
    public void init() {
        bitgetRestClient = bitgetRestClient();
    }

    /**
     * 获取账户信息列表
     * <a href="https://www.bitget.fit/zh-CN/api-doc/contract/account/Get-Account-List">获取账户信息列表</a>
     **/
    public ResponseResult<List<BitgetAccountsResp>> getAccounts() throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("productType", BG_PRODUCT_TYPE_USDT_FUTURES);
        Object accounts = bitgetRestClient.bitget().v2().mixAccount().getAccounts(paramMap);
        return toBean(toJson(accounts), ResponseResult.class, List.class, BitgetAccountsResp.class);
    }

    /**
     * 获取单个交易对账户信息
     * <a href="https://www.bitget.fit/zh-CN/api-doc/contract/account/Get-Single-Account">获取单个交易对账户信息</a>
     **/
    public ResponseResult<BitgetAccountResp> getAccount(String symbol) throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("symbol", symbol);
        paramMap.put("marginCoin", DEFAULT_CURRENCY_USDT);
        paramMap.put("productType", BG_PRODUCT_TYPE_USDT_FUTURES);
        Object account = bitgetRestClient.bitget().v2().mixAccount().getAccount(paramMap);
        return toBean(toJson(account), ResponseResult.class, BitgetAccountResp.class);
    }

    /**
     * 获取全部合约仓位信息
     * <a href="https://www.bitget.fit/zh-CN/api-doc/contract/position/get-all-position">获取全部合约仓位信息</a>
     **/
    public ResponseResult<List<BitgetAllPositionResp>> getAllPosition() throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("marginCoin", DEFAULT_CURRENCY_USDT);
        paramMap.put("productType", BG_PRODUCT_TYPE_USDT_FUTURES);
        Object allPosition = bitgetRestClient.bitget().v2().mixAccount().allPosition(paramMap);
        return toBean(toJson(allPosition), ResponseResult.class, List.class, BitgetAllPositionResp.class);
    }

    /**
     * 下单
     * 普通用户限速10次/S 根据uid限频
     * 交易员限速1次/S 根据uid限频
     * 描述
     * 单向持仓时，可省略tradeSide参数；
     * 双向持仓时，开多规则为：side=buy,tradeSide=open；开空规则为：side=sell,tradeSide=open；平多规则为：side=buy,tradeSide=close；平空规则为：side=sell,tradeSide=close
     * <a href="https://www.bitget.fit/zh-CN/api-doc/contract/trade/Place-Order">下单</a>
     **/
    public ResponseResult<BitgetPlaceOrderResp> placeOrder(String orderNo, String symbol, String size, String side, String tradeSide, String orderType, String marginMode) throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        //自定义订单id,幂等时间为20分钟
        paramMap.put("clientOid", orderNo);
        paramMap.put("symbol", symbol);
        //下单数量(基础币)
        paramMap.put("size", size);
        //下单方向 buy 买 sell 卖
        paramMap.put("side", side);
        //交易方向 开平仓，双向持仓模式下必填 open 开 close 平
        if (tradeSide != null && !tradeSide.isEmpty()) {
            paramMap.put("tradeSide", tradeSide);
        }
        //订单类型 limit 限价单， market 市价单
        paramMap.put("orderType", orderType);
        //止盈值 为空则默认不设止盈。
        //paramMap.put("presetStopSurplusPrice", null);
        //止损值 为空则默认不设止损。
        //paramMap.put("presetStopLossPrice", null);
        //下单价格。 订单类型为限价单(limit)时必填
        //paramMap.put("price", null);
        paramMap.put("marginMode", marginMode);
        paramMap.put("productType", BG_PRODUCT_TYPE_USDT_FUTURES);
        paramMap.put("marginCoin", DEFAULT_CURRENCY_USDT);
//        if (presetStopSurplusPrice != null && !presetStopSurplusPrice.isEmpty()) {
//            paramMap.put("presetStopSurplusPrice", presetStopSurplusPrice);
//        }
//        if (presetStopLossPrice != null && !presetStopLossPrice.isEmpty()) {
//            paramMap.put("presetStopLossPrice", presetStopLossPrice);
//        }
        Object placeOrder = bitgetRestClient.bitget().v2().mixOrder().placeOrder(paramMap);
        return toBean(toJson(placeOrder), ResponseResult.class, BitgetPlaceOrderResp.class);
    }


    /**
     * 获取订单详情
     * 限速规则: 10次/1s (uid)
     * <a href="https://www.bitget.fit/zh-CN/api-doc/contract/trade/Get-Order-Details">获取订单详情</a>
     **/
    public ResponseResult<BitgetOrderDetailResp> getOrderDetail(String symbol, String orderId) throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("orderId", orderId);
        paramMap.put("symbol", symbol);
        paramMap.put("productType", BG_PRODUCT_TYPE_USDT_FUTURES);
        Object rs = bitgetRestClient.bitget().v2().mixOrder().orderDetail(paramMap);
        return toBean(toJson(rs), ResponseResult.class, BitgetOrderDetailResp.class);
    }


    /**
     * 获取仓位档位梯度配置
     * 限速规则: 10次/1s (IP)
     * 描述
     * 获取某交易对的仓位档位梯度配置
     * <a href="https://www.bitget.fit/zh-CN/api-doc/contract/position/Get-Query-Position-Lever">获取某交易对的仓位档位梯度配置</a>
     **/
    public ResponseResult<List<BitgetQueryPositionLeverResp>> queryPositionLever(String symbol) throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("symbol", symbol);
        paramMap.put("productType", BG_PRODUCT_TYPE_USDT_FUTURES);
        Object queryPositionLever = bitgetRestClient.bitget().v2().mixMarket().queryPositionLever(paramMap);
        return toBean(toJson(queryPositionLever), ResponseResult.class, List.class, BitgetQueryPositionLeverResp.class);
    }

    /**
     * 获取可开数量
     * <a href="https://www.bitget.fit/zh-CN/api-doc/contract/account/Est-Open-Count">获取可开数量</a>
     **/
    public ResponseResult<BitgetOpenCountResp> openCount(String symbol, String openAmount, String openPrice, String leverage) throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("symbol", symbol);
        paramMap.put("openAmount", openAmount);
        paramMap.put("openPrice", openPrice);
        paramMap.put("leverage", leverage);
        paramMap.put("marginCoin", DEFAULT_CURRENCY_USDT);
        paramMap.put("productType", BG_PRODUCT_TYPE_USDT_FUTURES);
        Object openCount = bitgetRestClient.bitget().v2().mixAccount().openCount(paramMap);
        return toBean(toJson(openCount), ResponseResult.class, BitgetOpenCountResp.class);
    }


    /**
     * 订阅websocket合约公共频道
     **/
    public BitgetWsClient subscribeWsClientContractPublic(List<SubscribeReq> list, SubscriptionListener listener) {
        BitgetWsClient client = BitgetWsHandle.builder().pushUrl(wsPublicUrl).build();
        client.subscribe(list, listener);
        return client;
    }

    /**
     * 订阅websocket合约公共频道
     **/
    public BitgetWsClient subscribeWsClientContractPublic() {
        return BitgetWsHandle.builder().pushUrl(wsPublicUrl).build();
    }

    /**
     * 订阅websocket合约私有频道
     **/
    public void subscribeWsClientContractPrivate(List<SubscribeReq> list, SubscriptionListener listener) {
        BitgetWsClient client = BitgetWsHandle.builder()
                .pushUrl(wsPrivateUrl)
                .apiKey(apiKey)
                .secretKey(secretKey) // 如果是RSA类型则设置RSA私钥
                .passPhrase(passphrase)
                .signType(SignTypeEnum.RSA) // 如果你的apikey是RSA类型则主动设置签名类型为RSA
                .isLogin(true).build();
//        List<SubscribeReq> list = new ArrayList<>() {{
//            add(SubscribeReq.builder().instType("USDT-FUTURES").channel("ticker").instId("XRPUSDT").build());
//            add(SubscribeReq.builder().instType("USDT-FUTURES").channel("ticker").instId("BTCUSDT").build());
//        }};
        client.subscribe(list, listener);
    }


    /**
     * 获取现货K线数据
     * <a href="https://www.bitgetapps.com/zh-CN/api-doc/spot/market/Get-Candle-Data">获取K线数据</a>
     * 限速规则 20次/1s (IP)
     * 参数名	参数类型	是否必须	描述
     * symbol	String	是	交易对名称，如BTCUSDT
     * granularity	String	是	K线的时间颗粒度
     * 分钟：1min，5min，15min，30min
     * 小时：1h，4h，6h，12h
     * 天：1day，3day
     * 周：1week
     * 月：1M
     * 零时区小时线：6Hutc，12Hutc
     * 零时区日线：1Dutc ，3Dutc
     * 零时区周线：1Wutc
     * 零时区月线：1Mutc
     * 1m、3m、5m可以查一个月 ;15m可以查52天; 30m查62天; 1H可以查83天; 2H可以查120天; 4H可以查240天; 6H可以查360天
     * startTime	String	否	K线数据的时间起始点，即获取该时间戳以后的K线数据
     * Unix毫秒时间戳，例如1690196141868
     * endTime	String	否	K线数据的时间终止点，即获取该时间戳以前的K线数据
     * Unix毫秒时间戳，例如1690196141868
     * limit	String	否	查询条数
     * 默认100，最大1000
     **/
    public ResponseResult<List<BitgetCandlesResp>> getSpotMarketCandles(String symbol, String granularity, String startTime, String endTime, Integer limit) throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("symbol", symbol);
        paramMap.put("granularity", granularity);
        if (startTime != null) {
            paramMap.put("startTime", startTime);
        }
        if (endTime != null) {
            paramMap.put("endTime", endTime);
        }
        if (limit != null) {
            paramMap.put("limit", limit.toString());
        }
        ResponseResult<List<List<String>>> candlesResult = bitgetRestClient.bitget().v2().spotMarket().candles(paramMap);
        List<BitgetCandlesResp> candlesList = new ArrayList<>();
        List<List<String>> datas = candlesResult.getData();
        if (datas != null && !datas.isEmpty()) {
            for (List<String> data : datas) {
                candlesList.add(new BitgetCandlesResp(data));
            }
        }
        ResponseResult<List<BitgetCandlesResp>> newCandlesResult = new ResponseResult<>();
        newCandlesResult.setHttpCode(candlesResult.getHttpCode());
        newCandlesResult.setRequestTime(candlesResult.getRequestTime());
        newCandlesResult.setCode(candlesResult.getCode());
        newCandlesResult.setMsg(candlesResult.getMsg());
        newCandlesResult.setData(candlesList);
        return newCandlesResult;
    }


    /**
     * 获取合约K线数据
     * <a href="https://www.bitgetapps.com/zh-CN/api-doc/contract/market/Get-Candle-Data">获取合约K线数据</a>
     **/
    public ResponseResult<List<BitgetMixMarketCandlesResp>> getMinMarketCandles(String symbol, String productType, String granularity, Integer limit) throws IOException {
        return getMinMarketCandles(symbol, productType, granularity, limit, null, null);
    }

    public ResponseResult<List<BitgetMixMarketCandlesResp>> getMinMarketCandles(String symbol, String productType, String granularity, Integer limit, String startTime, String endTime) throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("symbol", symbol);
        paramMap.put("productType", productType);
        paramMap.put("granularity", granularity);
        if (limit != null) {
            paramMap.put("limit", limit.toString());
        }
        if (startTime != null && !startTime.isEmpty()) {
            paramMap.put("startTime", startTime);
        }
        if (endTime != null && !endTime.isEmpty()) {
            paramMap.put("endTime", endTime);
        }
        ResponseResult<List<List<String>>> candlesResult = bitgetRestClient.bitget().v2().mixMarket().candles(paramMap);
        List<BitgetMixMarketCandlesResp> candlesList = new ArrayList<>();
        List<List<String>> datas = candlesResult.getData();
        if (datas != null && !datas.isEmpty()) {
            for (List<String> data : datas) {
                candlesList.add(new BitgetMixMarketCandlesResp(data));
            }
        }
        ResponseResult<List<BitgetMixMarketCandlesResp>> newCandlesResult = new ResponseResult<>();
        newCandlesResult.setHttpCode(candlesResult.getHttpCode());
        newCandlesResult.setRequestTime(candlesResult.getRequestTime());
        newCandlesResult.setCode(candlesResult.getCode());
        newCandlesResult.setMsg(candlesResult.getMsg());
        newCandlesResult.setData(candlesList);
        return newCandlesResult;
    }

    /**
     * 调整杠杆
     * 限速规则: 5次/1s (uid)
     * 描述
     * 调整杠杆倍数
     * <a href="https://www.bitgetapps.com/zh-CN/api-doc/contract/account/Change-Leverage">调整杠杆倍数</a>
     * 参数名	参数类型	是否必须	描述
     * symbol	String	是	交易币对
     * productType	String	是	产品类型
     * USDT-FUTURES U本位合约
     * COIN-FUTURES 币本位合约
     * USDC-FUTURES USDC合约
     * SUSDT-FUTURES U本位合约模拟盘
     * SCOIN-FUTURES 币本位合约模拟盘
     * SUSDC-FUTURES USDC合约模拟盘
     * marginCoin	String	是	保证金币种 必须大写
     * leverage	String	是	杠杆倍数
     * holdSide	String	否	持仓方向
     * long：多仓；
     * short：空仓
     * 全仓模式：holdSide 参数不用填
     * 逐仓模式：单向持仓，holdSide 参数不用填; 双向持仓，holdSide 参数必填。
     **/
    public ResponseResult<BitgetSetLeverageResp> setLeverage(String symbol, String productType, String marginCoin, String leverage, String holdSide) throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("symbol", symbol);
        paramMap.put("productType", productType);
        paramMap.put("marginCoin", marginCoin);
        paramMap.put("leverage", leverage);
        if (holdSide != null && !holdSide.isEmpty()) {
            paramMap.put("holdSide", holdSide);
        }
        Object rs = bitgetRestClient.bitget().v2().mixAccount().setLeverage(paramMap);
        return toBean(toJson(rs), ResponseResult.class, BitgetSetLeverageResp.class);
    }

    /**
     * 调整单双向持仓模式
     * 描述
     * 产品类型下有仓位/委托时不能调整持仓模式。
     * 如果想变换用户在 所有symbol 合约上的持仓模式时，则需指定双向持仓或单向持仓。
     * 指定productType任意币对任意side存在仓位/委托的情况下，请求会失败
     **/
    public ResponseResult<BitgetSetPositionModeResp> setPositionMode(String productType, String posMode) throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("productType", productType);
        paramMap.put("posMode", posMode);
        Object rs = bitgetRestClient.bitget().v2().mixAccount().setPositionMode(paramMap);
        return toBean(toJson(rs), ResponseResult.class, BitgetSetPositionModeResp.class);
    }

    /**
     * 调整保证金模式
     * 限速规则: 5次/1s (uid)
     * 描述
     * 有持仓或委托时不能调用本接口
     * 参数名	参数类型	是否必须	描述
     * symbol	String	是	交易币对，如BTCUSDT
     * productType	String	是	产品类型
     * USDT-FUTURES U本位合约
     * COIN-FUTURES 币本位合约
     * USDC-FUTURES USDC合约
     * SUSDT-FUTURES U本位合约模拟盘
     * SCOIN-FUTURES 币本位合约模拟盘
     * SUSDC-FUTURES USDC合约模拟盘
     * marginCoin	String	是	保证金币种 必须大写
     * marginMode	String	是	保证金模式
     * isolated: 逐仓模式
     * crossed: 全仓模式
     **/
    public ResponseResult<BitgetSetMarginModeResp> setMarginMode(String symbol, String productType, String marginCoin, String marginMode) throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("symbol", symbol);
        paramMap.put("productType", productType);
        paramMap.put("marginCoin", marginCoin);
        paramMap.put("marginMode", marginMode);
        Object rs = bitgetRestClient.bitget().v2().mixAccount().setMarginMode(paramMap);
        return toBean(toJson(rs), ResponseResult.class, BitgetSetMarginModeResp.class);
    }


    /**
     * 获取现货行情信息
     * 限速规则 20次/1s (IP)
     * 描述
     * 获取公共行情信息，支持单个及批量查询
     **/
    public ResponseResult<List<BitgetTickersResp>> getSpotMarketTickers(String symbol) throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("symbol", symbol);
        Object rs = bitgetRestClient.bitget().v2().spotMarket().tickers(paramMap);
        return toBean(toJson(rs), ResponseResult.class, List.class, BitgetTickersResp.class);
    }

    /**
     * 获取单个合约交易对行情
     * 限速规则 20次/1s (IP)
     * 描述
     * 获取公共行情信息，支持单个及批量查询
     **/
    public ResponseResult<List<BitgetMixMarketTickerResp>> getMixMarketTicker(String symbol, String productType) throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("symbol", symbol);
        paramMap.put("productType", productType);
        Object rs = bitgetRestClient.bitget().v2().mixMarket().ticker(paramMap);
        return toBean(toJson(rs), ResponseResult.class, List.class, BitgetMixMarketTickerResp.class);
    }

    /**
     * 获取合约信息
     * 限频规则：20次/秒/IP
     * <p>
     * 描述
     * 获取合约详情信息。
     * <p>
     * HTTP
     * <a href="https://www.bitget.com/zh-CN/api-doc/contract/market/Get-All-Symbols-Contracts">获取合约信息</a>
     **/
    public ResponseResult<List<BitgetContractsResp>> getContracts(String symbol, String productType) throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("symbol", symbol);
        paramMap.put("productType", productType);
        Object rs = bitgetRestClient.bitget().v2().mixMarket().contracts(paramMap);
        return toBean(toJson(rs), ResponseResult.class, List.class, BitgetContractsResp.class);
    }


    /**
     * 止盈止损计划委托下单
     * <a href="https://www.bitgetapps.com/zh-CN/api-doc/contract/plan/Place-Tpsl-Order">止盈止损计划委托下单</a>
     **/
    public ResponseResult<BitgetPlaceTpslOrderResp> placeTpslOrder(BitgetPlaceTpslOrderParam param) throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("marginCoin", param.getMarginCoin());
        paramMap.put("productType", param.getProductType());
        paramMap.put("symbol", param.getSymbol());
        paramMap.put("planType", param.getPlanType());
        paramMap.put("triggerPrice", param.getTriggerPrice());
        if (param.getTriggerType() != null && !param.getTriggerType().isEmpty()) {
            paramMap.put("triggerType", param.getTriggerType());
        }
        if (param.getExecutePrice() != null && !param.getExecutePrice().isEmpty()) {
            paramMap.put("executePrice", param.getExecutePrice());
        }
        paramMap.put("holdSide", param.getHoldSide());
        if (param.getSize() != null && !param.getSize().isEmpty()) {
            paramMap.put("size", param.getSize());
        }
        if (param.getRangeRate() != null && !param.getRangeRate().isEmpty()) {
            paramMap.put("rangeRate", param.getRangeRate());
        }
        paramMap.put("clientOid", param.getClientOid());
        if (param.getStpMode() != null && !param.getStpMode().isEmpty()) {
            paramMap.put("stpMode", param.getStpMode());
        }

        Object rs = bitgetRestClient.bitget().v2().mixOrder().placeTpslOrder(paramMap);
        return toBean(toJson(rs), ResponseResult.class, BitgetPlaceTpslOrderResp.class);
    }

    /**
     * 修改止盈止损计划委托
     * <a href="https://www.bitgetapps.com/zh-CN/api-doc/contract/plan/Modify-Tpsl-Order">修改止盈止损计划委托</a>
     **/
    public ResponseResult<BitgetPlaceTpslOrderResp> modifyTpslOrder(BitgetModifyTpslOrderParam param) throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("marginCoin", param.getMarginCoin());
        paramMap.put("productType", param.getProductType());
        paramMap.put("symbol", param.getSymbol());
        paramMap.put("triggerPrice", param.getTriggerPrice());
        if (param.getTriggerType() != null && !param.getTriggerType().isEmpty()) {
            paramMap.put("triggerType", param.getTriggerType());
        }
        if (param.getExecutePrice() != null && !param.getExecutePrice().isEmpty()) {
            paramMap.put("executePrice", param.getExecutePrice());
        }
        if (param.getSize() != null) {
            paramMap.put("size", param.getSize());
        }
        if (param.getRangeRate() != null && !param.getRangeRate().isEmpty()) {
            paramMap.put("rangeRate", param.getRangeRate());
        }
        paramMap.put("orderId", param.getOrderId());

        Object rs = bitgetRestClient.bitget().v2().mixOrder().modifyTpslOrder(paramMap);
        return toBean(toJson(rs), ResponseResult.class, BitgetPlaceTpslOrderResp.class);
    }


    /**
     * 获取当前计划委托
     **/
    public ResponseResult<BitgetOrdersPlanPendingResp> getOrdersPlanPending(String planType, String productType) throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("planType", planType);
        paramMap.put("productType", productType);
        Object rs = bitgetRestClient.bitget().v2().mixOrder().ordersPlanPending(paramMap);
        return toBean(toJson(rs), ResponseResult.class, BitgetOrdersPlanPendingResp.class);
    }

    /**
     * 获取合约历史持仓列表
     * <a href="https://www.bitget.com/zh-CN/api-doc/contract/position/Get-History-Position">获取合约历史持仓列表</a>
     **/
    public ResponseResult<List<BitgetHistoryPositionResp>> getHistoryPosition(String symbol, Integer limit) throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("symbol", symbol);
        if (limit != null) {
            paramMap.put("limit", limit.toString());
        }
        ResponseResult<Map<String, Object>> historyPosition = bitgetRestClient.bitget().v2().mixAccount().historyPosition(paramMap);
        Map<String, Object> data = historyPosition.getData();
        ResponseResult<List<BitgetHistoryPositionResp>> result = new ResponseResult<>();
        result.setHttpCode(historyPosition.getHttpCode());
        result.setRequestTime(historyPosition.getRequestTime());
        result.setCode(historyPosition.getCode());
        result.setMsg(historyPosition.getMsg());
        if (data.containsKey("list")) {
            List<BitgetHistoryPositionResp> list = toList(toJson(data.get("list")), BitgetHistoryPositionResp.class);
            result.setData(list);
        }
        return result;
    }


    /**
     * 获取合约历史k线
     * <a href="https://www.bitget.com/zh-CN/api-doc/contract/market/Get-History-Candle-Data">获取合约历史K线数据</a>
     **/
    public ResponseResult<List<BitgetMixMarketCandlesResp>> getMixMarketHistoryCandles(String symbol, String productType, String granularity, Integer limit, String startTime, String endTime) throws IOException {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("symbol", symbol);
        paramMap.put("productType", productType);
        paramMap.put("granularity", granularity);
        if (limit != null) {
            paramMap.put("limit", limit.toString());
        }
        if (startTime != null && !startTime.isEmpty()) {
            paramMap.put("startTime", startTime);
        }
        if (endTime != null && !endTime.isEmpty()) {
            paramMap.put("endTime", endTime);
        }
        ResponseResult<List<List<String>>> candlesResult = bitgetRestClient.bitget().v2().mixMarket().historyCandles(paramMap);
        List<BitgetMixMarketCandlesResp> candlesList = new ArrayList<>();
        List<List<String>> datas = candlesResult.getData();
        if (datas != null && !datas.isEmpty()) {
            for (List<String> data : datas) {
                candlesList.add(new BitgetMixMarketCandlesResp(data));
            }
        }
        ResponseResult<List<BitgetMixMarketCandlesResp>> newCandlesResult = new ResponseResult<>();
        newCandlesResult.setHttpCode(candlesResult.getHttpCode());
        newCandlesResult.setRequestTime(candlesResult.getRequestTime());
        newCandlesResult.setCode(candlesResult.getCode());
        newCandlesResult.setMsg(candlesResult.getMsg());
        newCandlesResult.setData(candlesList);
        return newCandlesResult;
    }

}
