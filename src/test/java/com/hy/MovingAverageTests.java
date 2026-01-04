package com.hy;

import cn.hutool.core.date.DateUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.hy.common.enums.SymbolEnum;
import com.hy.common.utils.json.JsonUtil;
import com.hy.modules.cex.entity.DoubleMovingAveragePlaceOrder;
import com.hy.modules.cex.entity.DoubleMovingAverageStrategyConfig;
import com.hy.modules.dex.service.MovingAverageStrategyService;
import io.github.hyperliquid.sdk.HyperliquidClient;
import io.github.hyperliquid.sdk.model.info.CandleInterval;
import io.github.hyperliquid.sdk.model.subscription.CandleSubscription;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.hy.common.constants.HypeConstant.SIDE_SELL;

@SpringBootTest
public class MovingAverageTests {

    @Autowired
    MovingAverageStrategyService movingAverageStrategyService;


    @Test
    public void test1() {
        movingAverageStrategyService.managePositions();
    }

    @Test
    public void test2() {
        movingAverageStrategyService.managePositions();
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
        DoubleMovingAveragePlaceOrder order = movingAverageStrategyService.createPlaceOrder(config, SIDE_SELL, BigDecimal.valueOf(85000), BigDecimal.valueOf(86000));
        System.out.println(JsonUtil.toJson(order));
    }


    public static void main(String[] args) {
        System.out.println(DateUtil.now());
    }
}
