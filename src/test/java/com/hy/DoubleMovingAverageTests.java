package com.hy;

import com.bitget.custom.entity.BitgetMixMarketCandlesResp;
import com.bitget.openapi.dto.response.ResponseResult;
import com.hy.common.enums.BitgetAccountType;
import com.hy.common.enums.BitgetEnum;
import com.hy.common.service.BitgetCustomService;
import com.hy.common.utils.json.JsonUtil;
import com.hy.modules.contract.entity.DoubleMovingAverageData;
import com.hy.modules.contract.service.DoubleMovingAverageStrategyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.ta4j.core.BarSeries;

import java.io.IOException;
import java.util.List;
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
        String symbol = "ZECUSDT";
        String timeFrame = "4H";
        ResponseResult<List<BitgetMixMarketCandlesResp>> rs = bitgetSession.getMinMarketCandles(symbol, BG_PRODUCT_TYPE_USDT_FUTURES, timeFrame, 1000);
        if (rs.getData() == null || rs.getData().isEmpty()) return;
        if (rs.getData().size() < 500) return;
        BarSeries barSeries = DoubleMovingAverageStrategyService.buildSeriesFromBitgetCandles(rs.getData(), Objects.requireNonNull(BitgetEnum.getByCode(timeFrame)).getDuration());
        DoubleMovingAverageData data = DoubleMovingAverageStrategyService.calculateIndicators(barSeries);
        System.out.println(JsonUtil.toJson(data));
        System.out.println(doubleMovingAverageStrategyService.isStrictMATrendConfirmed(data));
    }
}
