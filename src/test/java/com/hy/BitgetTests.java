package com.hy;

import com.bitget.custom.entity.*;
import com.bitget.openapi.common.client.BitgetRestClient;
import com.bitget.openapi.common.domain.ClientParameter;
import com.bitget.openapi.common.enums.SignTypeEnum;
import com.bitget.openapi.common.enums.SupportedLocaleEnum;
import com.bitget.openapi.dto.response.ResponseResult;
import com.hy.common.service.BitgetOldCustomService;
import com.hy.common.utils.json.JsonUtil;
import com.hy.modules.contract.service.DualMovingAverageStrategyV1Service;
import org.jasypt.util.text.AES256TextEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static com.hy.common.constants.BitgetConstant.*;

@SpringBootTest
class BitgetTests {


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

    @Autowired
    BitgetOldCustomService bitgetCustomService;

    @Autowired
    DualMovingAverageStrategyV1Service dualMovingAverageStrategyService;

    @Test
    public void t1() throws IOException, InterruptedException {
        System.out.println("明文apiKey: " + apiKey);
        System.out.println("明文passphrase: " + passphrase);
        System.out.println("明文secretKey: " + secretKey);
        AES256TextEncryptor textEncryptor = new AES256TextEncryptor();
        textEncryptor.setPassword("*"); // 这是加密密钥，等下会用于解密
        String encrypted = textEncryptor.encrypt(passphrase); // 明文密码
        System.out.println("ENC(" + encrypted + ")");
    }


    public BitgetRestClient bitgetRestClient() {
        ClientParameter parameter = ClientParameter.builder()
                .apiKey(apiKey)
                .secretKey(secretKey) // 如果是RSA类型则设置为RSA私钥
                .passphrase(passphrase)
                .baseUrl(baseUrl)
                .signType(SignTypeEnum.RSA) // 如果你的apikey是RSA类型则主动设置签名类型为RSA
                .locale(SupportedLocaleEnum.ZH_CN.getName()).build();
        return BitgetRestClient.builder().configuration(parameter).build();
    }

    @Test
    public void t2() throws IOException {
        ResponseResult<List<BitgetAccountsResp>> accounts = bitgetCustomService.getAccounts();
        System.out.println(JsonUtil.toJson(accounts));
    }

    @Test
    public void t3() throws IOException {
        ResponseResult<BitgetAccountResp> account = bitgetCustomService.getAccount("RDNTUSDT");
        System.out.println(account);
        //{"httpCode":"200","code":"00000","msg":"success","requestTime":1751164970879,"data":[{"marginCoin":"USDT","locked":"0","available":"206.58463748","crossedMaxAvailable":"206.58463748","isolatedMaxAvailable":"206.58463748","maxTransferOut":"206.58463748","accountEquity":"206.58463748","usdtEquity":"206.58463748202","btcEquity":"0.001924669559","crossedRiskRate":"0","coupon":"0"}]}

    }

    @Test
    public void t4() {

    }

    @Test
    public void t5() throws IOException {
        ResponseResult<List<BitgetAllPositionResp>> allPosition = bitgetCustomService.getAllPosition();
        System.out.println(JsonUtil.toJson(allPosition));
        //{"httpCode":"200","code":"00000","msg":"success","requestTime":1717489920046,"data":[{"marginCoin":"USDT","symbol":"RDNTUSDT","holdSide":"short","openDelegateSize":"0","marginSize":"6.3993","available":"30","locked":"0","total":"30","leverage":"1","achievedProfits":"0","openPriceAvg":"0.21331","marginMode":"crossed","posMode":"hedge_mode","liquidationPrice":"2.059608083806","keepMarginRate":"0.02","markPrice":"0.21339","marginRatio":"0.002327505513","cTime":"1717489861162"}]}
        //{"httpCode":"200","code":"00000","msg":"success","requestTime":1750953550023,"data":[{"marginCoin":"USDT","symbol":"ETHUSDT","holdSide":"long","openDelegateSize":"0","marginSize":"48.4478","available":"0.02","locked":"0","total":"0.02","leverage":"1","achievedProfits":"0","openPriceAvg":"2422.39","marginMode":"isolated","posMode":"hedge_mode","unrealizedPL":"0.1872","liquidationPrice":"0","keepMarginRate":"0.005","markPrice":"2431.75","marginRatio":"0.0056","cTime":"1750952242818"},{"marginCoin":"USDT","symbol":"BTCUSDT","holdSide":"long","openDelegateSize":"0","marginSize":"42.71004","available":"0.0004","locked":"0","total":"0.0004","leverage":"1","achievedProfits":"0","openPriceAvg":"106775.1","marginMode":"isolated","posMode":"hedge_mode","unrealizedPL":"0.16276","liquidationPrice":"0","keepMarginRate":"0.004","markPrice":"107182","marginRatio":"0.0046","cTime":"1750952201856"}]}
    }

    @Test
    public void t6() throws IOException {
        String orderNo = "1750958278014";
        String symbol = "ETHUSDT";
        String size = "0.04";
        String side = BG_SIDE_SELL;
        String tradeSide = null;// BG_TRADE_SIDE_OPEN;
        String orderType = BG_ORDER_TYPE_MARKET;
        String marginMode = BG_MARGIN_MODE_CROSSED;

        ResponseResult<BitgetPlaceOrderResp> bpor = bitgetCustomService.placeOrder(orderNo, symbol, size, side, tradeSide, orderType, marginMode);
        System.out.println(JsonUtil.toJson(bpor));
        dualMovingAverageStrategyService.setPlaceTpslOrder(symbol, "2500", null, null, "sell", "pos_loss");

        //{"httpCode":"200","code":"00000","msg":"success","requestTime":1751161257943,"data":{"clientOid":"1750958278008","orderId":"1323031696869281794"}}
    }

    @Test
    public void t7() throws IOException {
        ResponseResult<BitgetOrderDetailResp> rdntusdt = bitgetCustomService.getOrderDetail("ETHUSDT", "1323031696869281794");
        System.out.println(JsonUtil.toJson(rdntusdt));
        //{"code":"40109","httpCode":"400","msg":"查不到该订单的数据，请确认订单号","requestTime":1717492614921}
        //{"httpCode":"200","code":"00000","msg":"success","requestTime":1751161353574,"data":{"symbol":"ETHUSDT","size":"0.02","orderId":"1323031696869281794","clientOid":"1750958278008","baseVolume":"0.02","fee":"-0.02917512","priceAvg":"2431.26","state":"filled","side":"buy","force":"gtc","totalProfits":"0","posSide":"net","marginCoin":"USDT","presetStopLossPrice":"2400","quoteVolume":"48.6252","orderType":"market","leverage":"2","marginMode":"crossed","reduceOnly":"NO","enterPointSource":"API","tradeSide":"buy_single","posMode":"one_way_mode","orderSource":"market","cTime":"1751161257957","uTime":"1751161258026"}}
        //{"httpCode":"200","code":"00000","msg":"success","requestTime":1751162461162,"data":{"symbol":"ETHUSDT","size":"0.02","orderId":"1323031696869281794","clientOid":"1750958278008","baseVolume":"0.02","fee":"-0.02917512","priceAvg":"2431.26","state":"filled","side":"buy","force":"gtc","totalProfits":"0","posSide":"net","marginCoin":"USDT","presetStopLossPrice":"2400","quoteVolume":"48.6252","orderType":"market","leverage":"2","marginMode":"crossed","reduceOnly":"NO","enterPointSource":"API","tradeSide":"buy_single","posMode":"one_way_mode","orderSource":"market","cTime":"1751161257957","uTime":"1751161258026"}}
    }

    @Test
    public void t8() throws IOException {
        ResponseResult<List<BitgetQueryPositionLeverResp>> rdntusdt = bitgetCustomService.queryPositionLever("RDNTUSDT");
        System.out.println(JsonUtil.toJson(rdntusdt));
    }

    @Test
    public void t9() throws IOException {
        ResponseResult<BitgetOpenCountResp> rdntusdt = bitgetCustomService.openCount("RDNTUSDT", "5", "0.1", "5");
        System.out.println(JsonUtil.toJson(rdntusdt));
    }

    @Test
    public void t10() throws IOException {
        ResponseResult<List<BitgetMixMarketCandlesResp>> rs = bitgetCustomService.getMinMarketCandles("BTCUSDT", BG_PRODUCT_TYPE_USDT_FUTURES, "4H", 500);
        System.out.println(JsonUtil.toJson(rs));
        BarSeries barSeries = DualMovingAverageStrategyV1Service.buildSeriesFromBitgetCandles(rs.getData(), Duration.ofHours(1));
        List<Num> n = DualMovingAverageStrategyV1Service.calculateIndicators(barSeries);
        System.out.println("n0 = " + n.get(0) + " n1 = " + n.get(1));
    }

    @Test
    public void t11() throws IOException {

    }

    @Test
    public void t12() throws IOException {
        ResponseResult<BitgetSetLeverageResp> rs = bitgetCustomService.setLeverage("SOLUSDT", "USDT-FUTURES", "USDT", "1", null);
        System.out.println(JsonUtil.toJson(rs));
    }


    @Test
    public void t13() throws IOException {
        BitgetPlaceTpslOrderParam param = new BitgetPlaceTpslOrderParam();
        param.setMarginCoin("USDT");
        param.setProductType("USDT-FUTURES");
        param.setSymbol("ETHUSDT");
        /*止盈止损类型
        profit_plan：止盈计划
        loss_plan：止损计划
        moving_plan：移动止盈止损
        pos_profit：仓位止盈
        pos_loss：仓位止损*/
        param.setPlanType("profit_plan");
        param.setTriggerPrice("2400");
        param.setTriggerType("fill_price");
        param.setExecutePrice("2400");
        param.setHoldSide("sell");
        param.setSize("0.02");
        param.setClientOid("1750958278110");
        ResponseResult<BitgetPlaceTpslOrderResp> result = bitgetCustomService.placeTpslOrder(param);
        System.out.println(JsonUtil.toJson(result));
    }

    @Test
    public void t14() throws IOException {
        dualMovingAverageStrategyService.setBitgetAccount();
    }

    @Test
    public void t15() throws IOException {
        ResponseResult<List<BitgetMixMarketTickerResp>> rs = bitgetCustomService.getMixMarketTicker("BTCUSDT", BG_PRODUCT_TYPE_USDT_FUTURES);
        System.out.println(JsonUtil.toJson(rs));
    }

    @Test
    public void t16() throws IOException {
        ResponseResult<List<BitgetContractsResp>> rs = bitgetCustomService.getContracts("SOLUSDT", "USDT-FUTURES");
        System.out.println(JsonUtil.toJson(rs));
        //{"httpCode":"200","code":"00000","msg":"success","requestTime":1751040116004,"data":[{"symbol":"BTCUSDT","baseCoin":"BTC","quoteCoin":"USDT","buyLimitPriceRatio":"0.05","sellLimitPriceRatio":"0.05","feeRateUpRatio":"0.005","makerFeeRate":"0.0002","takerFeeRate":"0.0006","openCostUpRatio":"0.01","supportMarginCoins":["USDT"],"minTradeNum":"0.0001","priceEndStep":"1","volumePlace":"4","pricePlace":"1","sizeMultiplier":"0.0001","symbolType":"perpetual","minTradeUSDT":"5","maxSymbolOrderNum":"200","maxProductOrderNum":"1000","maxPositionNum":"150","symbolStatus":"normal","offTime":"-1","limitOpenTime":"-1","fundInterval":"8","minLever":"1","maxLever":"125","posLimit":"0.1","maxMarketOrderQty":"220","maxOrderQty":"1200"}]}
        //{"httpCode":"200","code":"00000","msg":"success","requestTime":1751040321976,"data":[{"symbol":"ETHUSDT","baseCoin":"ETH","quoteCoin":"USDT","buyLimitPriceRatio":"0.05","sellLimitPriceRatio":"0.05","feeRateUpRatio":"0.005","makerFeeRate":"0.0002","takerFeeRate":"0.0006","openCostUpRatio":"0.01","supportMarginCoins":["USDT"],"minTradeNum":"0.01","priceEndStep":"1","volumePlace":"2","pricePlace":"2","sizeMultiplier":"0.01","symbolType":"perpetual","minTradeUSDT":"5","maxSymbolOrderNum":"200","maxProductOrderNum":"1000","maxPositionNum":"150","symbolStatus":"normal","offTime":"-1","limitOpenTime":"-1","fundInterval":"8","minLever":"1","maxLever":"100","posLimit":"0.1","maxMarketOrderQty":"1900","maxOrderQty":"9900"}]}
        //{"httpCode":"200","code":"00000","msg":"success","requestTime":1751040457268,"data":[{"symbol":"SOLUSDT","baseCoin":"SOL","quoteCoin":"USDT","buyLimitPriceRatio":"0.05","sellLimitPriceRatio":"0.05","feeRateUpRatio":"0.005","makerFeeRate":"0.0002","takerFeeRate":"0.0006","openCostUpRatio":"0.01","supportMarginCoins":["USDT"],"minTradeNum":"0.1","priceEndStep":"1","volumePlace":"1","pricePlace":"3","sizeMultiplier":"0.1","symbolType":"perpetual","minTradeUSDT":"5","maxSymbolOrderNum":"200","maxProductOrderNum":"1000","maxPositionNum":"150","symbolStatus":"normal","offTime":"-1","limitOpenTime":"-1","fundInterval":"8","minLever":"1","maxLever":"100","posLimit":"0.1","maxMarketOrderQty":"13000","maxOrderQty":"130000"}]}
    }

    @Test
    public void t17() throws IOException {
        /*
         * 计划委托类型
         normal_plan: 普通计划委托
         track_plan: 追踪委托
         profit_loss: 止盈止损类委托(包含了：profit_plan：止盈计划, loss_plan：止损计划, moving_plan：移动止盈止损，pos_profit：仓位止盈，pos_loss：仓位止损)**/
        ResponseResult<BitgetOrdersPlanPendingResp> ordersPlanPending = bitgetCustomService.getOrdersPlanPending("profit_loss", "USDT-FUTURES");
        System.out.println(JsonUtil.toJson(ordersPlanPending));
    }

    @Test
    public void t18() {

    }

    @Test
    public void t19() throws IOException, InterruptedException {
        BitgetModifyTpslOrderParam param = new BitgetModifyTpslOrderParam();
        param.setOrderId("1323405999546675201");
        param.setMarginCoin("USDT");
        param.setProductType("USDT-FUTURES");
        param.setSymbol("ETHUSDT");
        param.setTriggerPrice("2468.88");//2468.88
        param.setTriggerType("fill_price");
        param.setSize("0.04");
        ResponseResult<BitgetPlaceTpslOrderResp> result = bitgetCustomService.modifyTpslOrder(param);
        System.out.println(JsonUtil.toJson(result));
    }

    public static void main(String[] args) {

    }

}
