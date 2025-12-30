package com.hy;

import cn.hutool.core.date.DateUtil;
import com.bitget.custom.entity.BitgetMixMarketCandlesResp;
import com.bitget.openapi.dto.response.ResponseResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.hy.common.enums.BitgetAccountType;
import com.hy.common.enums.BitgetEnum;
import com.hy.common.enums.SymbolEnum;
import com.hy.common.service.BitgetCustomService;
import com.hy.common.utils.json.JsonUtil;
import com.hy.modules.contract.entity.DoubleMovingAverageData;
import com.hy.modules.contract.entity.DoubleMovingAveragePlaceOrder;
import com.hy.modules.contract.entity.DoubleMovingAverageStrategyConfig;
import com.hy.modules.contract.service.DoubleMovingAverageStrategyService;
import com.hy.modules.contract.service.DoubleMovingAverageStrategyV2Service;
import io.github.hyperliquid.sdk.HyperliquidClient;
import io.github.hyperliquid.sdk.model.info.CandleInterval;
import io.github.hyperliquid.sdk.model.subscription.CandleSubscription;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.ta4j.core.BarSeries;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.hy.common.constants.BitgetConstant.BG_PRODUCT_TYPE_USDT_FUTURES;
import static com.hy.common.constants.HypeConstant.HYPE_SIDE_SELL;

@SpringBootTest
public class DoubleMovingAverageTests {

    @Autowired
    DoubleMovingAverageStrategyV2Service doubleMovingAverageStrategyService;

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
    public void test5() throws IOException, InterruptedException {
        HyperliquidClient client = HyperliquidClient.builder().build();
        List<String> symbols = List.of("BTC", "ETH", "SOL");
        for (String symbol : symbols) {
            client.getInfo().subscribe(CandleSubscription.of(symbol, "1m"),
                    msg -> {
                        JsonNode data = msg.get("data");
                        if (data != null) {
                            //String open = data.path("o").asText();
                            //String high = data.path("h").asText();
                            //String low = data.path("l").asText();
                            String close = data.path("c").asText();
                            //String volume = data.path("v").asText();
                            System.out.println(symbol + ": " + close);
                        }
                    });
        }

        //线程等待
        while (true) {
            Thread.sleep(1000000);
        }
    }

    @Test
    public void createPlaceOrder() throws IOException {
        Map<String, DoubleMovingAverageStrategyConfig> CONFIG_MAP = new ConcurrentHashMap<>() {
            {
                put(SymbolEnum.BTCUSDC.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.BTCUSDC.getCode(), CandleInterval.HOUR_4.getCode(), 4, 1, 40, BigDecimal.valueOf(20.0), BigDecimal.valueOf(10.0)));
                put(SymbolEnum.ETHUSDC.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.ETHUSDC.getCode(), CandleInterval.HOUR_4.getCode(), 2, 2, 25, BigDecimal.valueOf(20.0), BigDecimal.valueOf(15.0)));
                //put(SymbolEnum.SOLUSDT.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.SOLUSDT.getCode(), CandleInterval.HOUR_4.getCode(), 1, 3, 20, BigDecimal.valueOf(20.0), BigDecimal.valueOf(20.0)));
                //put(SymbolEnum.ZECUSDT.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.ZECUSDT.getCode(), CandleInterval.H4.getCode(), 3, 2, 75, BigDecimal.valueOf(10.0), BigDecimal.valueOf(22.0)));
                //put(SymbolEnum.HYPEUSDT.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.HYPEUSDT.getCode(), CandleInterval.H4.getCode(), 2, 3, 75, BigDecimal.valueOf(10.0), BigDecimal.valueOf(25.0)));
                //put(SymbolEnum.DOGEUSDT.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.DOGEUSDT.getCode(), CandleInterval.H4.getCode(), 0, 5, 75, BigDecimal.valueOf(10.0), BigDecimal.valueOf(25.0)));
                //put(SymbolEnum.BNBUSDT.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.BNBUSDT.getCode(), CandleInterval.H4.getCode(), 2, 2, 75, BigDecimal.valueOf(10.0), BigDecimal.valueOf(20.0)));
            }
        };
        DoubleMovingAverageStrategyConfig config = CONFIG_MAP.get(SymbolEnum.BTCUSDC.getCode());
        DoubleMovingAveragePlaceOrder order = doubleMovingAverageStrategyService.createPlaceOrder(config, HYPE_SIDE_SELL, BigDecimal.valueOf(85000), BigDecimal.valueOf(86000));
        System.out.println(JsonUtil.toJson(order));
    }


    public static void main(String[] args) {
        System.out.println(DateUtil.now());
    }
}
