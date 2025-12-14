package com.hy;

import com.bitget.custom.entity.*;
import com.bitget.openapi.dto.response.ResponseResult;
import com.hy.common.enums.BitgetAccountType;
import com.hy.common.enums.Direction;
import com.hy.common.service.BitgetCustomService;
import com.hy.common.utils.json.JsonUtil;
import com.hy.modules.contract.entity.MartingaleOrderLevel;
import com.hy.modules.contract.service.MartingaleStrategyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static com.hy.common.constants.BitgetConstant.BG_RESPONSE_CODE_SUCCESS;

@SpringBootTest
public class MartingaleStrategyTests {

    @Autowired
    BitgetCustomService bitgetCustomService;

    @Autowired
    MartingaleStrategyService martingaleStrategyService;


    @Test
    public void batchPlaceOrder() throws IOException {
        BitgetCustomService.BitgetSession bitgetSession = bitgetCustomService.use(BitgetAccountType.MARTINGALE);

        BitgetBatchPlaceOrderParam param = new BitgetBatchPlaceOrderParam();
        param.setSymbol("BTCUSDT");
        param.setProductType("USDT-FUTURES");
        param.setMarginCoin("USDT");
        param.setMarginMode("crossed");
        List<BitgetBatchPlaceOrderParam.Order> orderList = new ArrayList<>();
        long millis = System.currentTimeMillis();
        BitgetBatchPlaceOrderParam.Order order0 = new BitgetBatchPlaceOrderParam.Order();
        order0.setClientOid(String.valueOf(millis));
        order0.setSize("0.0001");
        //order0.setPrice("114000");
        order0.setSide("buy");
        //order0.setTradeSide();//开平仓双向持仓模式下必填，单向持仓时不要填，否则会报错
        order0.setOrderType("market");
        order0.setForce("gtc");
        BitgetBatchPlaceOrderParam.Order order1 = new BitgetBatchPlaceOrderParam.Order();
        order1.setClientOid(String.valueOf(millis + 1));
        order1.setSize("0.001");
        order1.setPrice("114000");
        order1.setSide("buy");
        //order1.setTradeSide();//开平仓双向持仓模式下必填，单向持仓时不要填，否则会报错
        order1.setOrderType("limit");
        order1.setForce("gtc");
        BitgetBatchPlaceOrderParam.Order order2 = new BitgetBatchPlaceOrderParam.Order();
        order2.setClientOid(String.valueOf(millis + 2));
        order2.setSize("0.001");
        order2.setPrice("113000");
        order2.setSide("buy");
        //order2.setTradeSide();//开平仓双向持仓模式下必填，单向持仓时不要填，否则会报错
        order2.setOrderType("limit");
        order2.setForce("gtc");
        BitgetBatchPlaceOrderParam.Order order3 = new BitgetBatchPlaceOrderParam.Order();
        order3.setClientOid(String.valueOf(millis + 3));
        order3.setSize("0.001");
        order3.setPrice("112000");
        order3.setSide("buy");
        //order2.setTradeSide();//开平仓双向持仓模式下必填，单向持仓时不要填，否则会报错
        order3.setOrderType("limit");
        order3.setForce("gtc");

        orderList.add(order0);
        orderList.add(order1);
        orderList.add(order2);
        orderList.add(order3);
        param.setOrderList(orderList);
        ResponseResult<BitgetBatchPlaceOrderResp> result = bitgetSession.batchPlaceOrder(param);
        System.out.println("批量下单结果: " + JsonUtil.toJson(result));
    }


    @Test
    public void batchCancelOrders() throws IOException {
        BitgetCustomService.BitgetSession bitgetSession = bitgetCustomService.use(BitgetAccountType.MARTINGALE);
        BitgetBatchCancelOrdersParam param = new BitgetBatchCancelOrdersParam();
        param.setSymbol("BTCUSDT");
        param.setProductType("USDT-FUTURES");
        param.setMarginCoin("USDT");
        ResponseResult<BitgetBatchCancelOrdersResp> result = bitgetSession.batchCancelOrders(param);
        System.out.println("批量撤单结果: " + JsonUtil.toJson(result));
    }

    @Test
    public void getOrdersPending() {
        BitgetCustomService.BitgetSession bitgetSession = bitgetCustomService.use(BitgetAccountType.MARTINGALE);
        try {
            ResponseResult<BitgetOrdersPendingResp> result = bitgetSession.getOrdersPending("BTCUSDT", "USDT-FUTURES");
            System.out.println("查询当前委托结果: " + JsonUtil.toJson(result));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void getAllPosition() throws IOException {
        BitgetCustomService.BitgetSession bitgetSession = bitgetCustomService.use(BitgetAccountType.MARTINGALE);

        // 获取当前持仓
        ResponseResult<List<BitgetAllPositionResp>> positionResp = bitgetSession.getAllPosition();
        if (!BG_RESPONSE_CODE_SUCCESS.equals(positionResp.getCode())) {
            return;
        }
        List<BitgetAllPositionResp> positions = Optional.ofNullable(positionResp.getData()).orElse(Collections.emptyList());
        Map<String, BitgetAllPositionResp> positionMap = positions.stream().collect(Collectors.toMap(BitgetAllPositionResp::getSymbol, p -> p, (existing, replacement) -> existing));
        System.out.println("当前持仓: " + JsonUtil.toJson(positionMap));
    }

    @Test
    public void getAccounts() throws IOException {
        BitgetCustomService.BitgetSession bitgetSession = bitgetCustomService.use(BitgetAccountType.MARTINGALE);

        ResponseResult<List<BitgetAccountsResp>> accounts = bitgetSession.getAccounts();

        System.out.println("账户信息列表: " + JsonUtil.toJson(accounts));
    }

    @Test
    public void startMartingaleStrategy() {
        martingaleStrategyService.startOrderConsumer();
        martingaleStrategyService.startMartingaleStrategy();
        //线程等待
        try {
            Thread.sleep(1000000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void t1() {
        martingaleStrategyService.managePositions();
    }

    @Test
    public void t2() {
        martingaleStrategyService.initializeConfig();
    }

    @Test
    public void t3() {
        martingaleStrategyService.loadDefaultConfig();
        System.out.println(JsonUtil.toJson(MartingaleStrategyService.STRATEGY_CONFIG_MAP));
    }

    public static void main(String[] args) {
        BigDecimal entryPrice = new BigDecimal("89300"); // 初始下单价
        BigDecimal baseStep = new BigDecimal("0.02");         // 2%
        BigDecimal amountMultiplier = new BigDecimal("1.1");  // 加仓金额倍数
        BigDecimal stepMultiplier = new BigDecimal("1.1");    // 加仓价差倍数
        BigDecimal leverage = new BigDecimal("1");            // 杠杆倍数
        BigDecimal maxTotalMargin = new BigDecimal("40000"); // 最大投入保证金
        int maxAddCount = 15;

        List<MartingaleOrderLevel> plan = MartingaleStrategyService.generateOrderPlanMaxMargin(
                entryPrice,
                baseStep,
                maxAddCount,
                amountMultiplier,
                stepMultiplier,
                leverage,
                maxTotalMargin,
                Direction.LONG,
                2, 4
        );

        plan.forEach(System.out::println);

        // 输出总投入金额
        BigDecimal total = plan.stream()
                .map(l -> l.margin)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.println("总投入保证金=" + total.setScale(2, RoundingMode.HALF_UP));
    }

}
