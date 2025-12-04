package com.hy;

import cn.hutool.core.date.DateUtil;
import com.bitget.custom.entity.BitgetMixMarketCandlesResp;
import com.bitget.custom.entity.BitgetOrderDetailResp;
import com.bitget.custom.entity.BitgetOrdersPlanPendingResp;
import com.bitget.openapi.dto.response.ResponseResult;
import com.hy.common.enums.BitgetAccountType;
import com.hy.common.enums.BitgetEnum;
import com.hy.common.service.BitgetCustomService;
import com.hy.common.utils.json.JsonUtil;
import com.hy.modules.contract.entity.DoubleMovingAverageData;
import com.hy.modules.contract.entity.DoubleMovingAveragePlaceOrder;
import com.hy.modules.contract.service.DoubleMovingAverageStrategyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.ta4j.core.BarSeries;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.hy.common.constants.BitgetConstant.BG_PRODUCT_TYPE_USDT_FUTURES;

@SpringBootTest
public class DoubleMovingAverageTests {

    @Autowired
    DoubleMovingAverageStrategyService doubleMovingAverageStrategyService;

    @Autowired
    BitgetCustomService bitgetCustomService;

    @Test
    public void test1() {
        doubleMovingAverageStrategyService.managePositions();
    }

    @Test
    public void test2() {
        doubleMovingAverageStrategyService.managePositions();
    }

    @Test
    public void test3() throws IOException {
        BitgetCustomService.BitgetSession bitgetSession = bitgetCustomService.use(BitgetAccountType.RANGE);
        String symbol = "HYPEUSDT";
        String timeFrame = "4H";
        ResponseResult<List<BitgetMixMarketCandlesResp>> rs = bitgetSession.getMinMarketCandles(symbol, BG_PRODUCT_TYPE_USDT_FUTURES, timeFrame, 1000);
        if (rs.getData() == null || rs.getData().isEmpty()) return;
        if (rs.getData().size() < 500) return;
        BarSeries barSeries = DoubleMovingAverageStrategyService.buildSeriesFromBitgetCandles(rs.getData(), Objects.requireNonNull(BitgetEnum.getByCode(timeFrame)).getDuration());
        DoubleMovingAverageData data = DoubleMovingAverageStrategyService.calculateIndicators(barSeries, 2);
        System.out.println(JsonUtil.toJson(data));
        System.out.println(doubleMovingAverageStrategyService.isStrictMATrendConfirmed(data));
    }


    @Test
    public void testEamil() throws IOException {
        BitgetCustomService.BitgetSession bitgetSession = bitgetCustomService.use(BitgetAccountType.RANGE);
        DoubleMovingAveragePlaceOrder order = JsonUtil.toBean("{\"clientOid\":\"1996403203935412224\",\"symbol\":\"HYPEUSDT\",\"size\":\"1.95\",\"side\":\"buy\",\"orderType\":\"market\",\"marginMode\":\"isolated\",\"stopLossPrice\":\"32.590\",\"takeProfitPrice\":\"40.810\",\"takeProfitSize\":\"0.98\",\"accountBalance\":76.61968933,\"leverage\":7}", DoubleMovingAveragePlaceOrder.class);
        ResponseResult<BitgetOrderDetailResp> detail = bitgetSession.getOrderDetailByClientOid(order.getSymbol(), order.getClientOid());
        String html = doubleMovingAverageStrategyService.buildOrderEmailContent(order, detail);
        // 发送HTML格式的邮件通知
        doubleMovingAverageStrategyService.sendHtmlEmail(DateUtil.now() + " 双均线策略下单成功 ✅", html);

    }

    @Test
    public void test4() throws IOException {
        Map<String, List<BitgetOrdersPlanPendingResp.EntrustedOrder>> ordersPlanPending = doubleMovingAverageStrategyService.getOrdersPlanPending();
        System.out.println(JsonUtil.toJson(ordersPlanPending));
    }

    public static void main(String[] args) {
        System.out.println(DateUtil.now());
    }
}
