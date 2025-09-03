package com.hy;

import cn.hutool.core.date.DateUtil;
import com.bitget.custom.entity.*;
import com.bitget.openapi.dto.response.ResponseResult;
import com.hy.common.service.BitgetCustomService;
import com.hy.common.utils.json.JsonUtil;
import com.hy.modules.contract.service.RangeTradingStrategyV5Service;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hy.common.constants.BitgetConstant.BG_PLAN_TYPE_PROFIT_LOSS;
import static com.hy.common.constants.BitgetConstant.BG_PRODUCT_TYPE_USDT_FUTURES;
import static com.hy.common.utils.num.BigDecimalUtils.gte;
import static com.hy.common.utils.num.BigDecimalUtils.lte;

@SpringBootTest
class RangeTradingStrategyV5ServiceTests {


    @Autowired
    BitgetCustomService bitgetCustomService;

    @Autowired
    RangeTradingStrategyV5Service rangeTradingStrategyV5Service;

    public static void main(String[] args) {
        BigDecimal realityOpenAmount = new BigDecimal("0.5");
        System.out.println(realityOpenAmount.setScale(0, RoundingMode.HALF_UP));
    }

    @Test
    public void t0() throws IOException, InterruptedException {
        ResponseResult<List<BitgetHistoryPositionResp>> result = bitgetCustomService.getHistoryPosition("ETHUSDT", 100);
        System.out.println(JsonUtil.toJson(result));

        Map<String, List<BitgetHistoryPositionResp>> bhpMap = result.getData().stream().collect(Collectors.groupingBy(BitgetHistoryPositionResp::getSymbol));
        List<BitgetHistoryPositionResp> ethusdt = bhpMap.get("ETHUSDT");
        //ethusdt 通过创建时间降序排序
        ethusdt.sort(Comparator.comparing(BitgetHistoryPositionResp::getCtime).reversed());
        System.out.println(JsonUtil.toJson(ethusdt));
    }

    @Test
    public void t1() throws IOException, InterruptedException {
        Integer leverage = rangeTradingStrategyV5Service.calculateAndSetLeverage("ETHUSDT");
        System.out.println("ETHUSDT杠杆: " + leverage);
    }

    @Test
    public void getAllPosition() throws IOException {
        ResponseResult<List<BitgetAllPositionResp>> allPosition = bitgetCustomService.getAllPosition();
        System.out.println(JsonUtil.toJson(allPosition));
        Map<String, BitgetAllPositionResp> positionMap = allPosition.getData().stream().collect(Collectors.toMap(BitgetAllPositionResp::getSymbol, p -> p, (existing, replacement) -> existing));
        System.out.println(JsonUtil.toJson(positionMap));
    }

    @Test
    public void getOrdersPlanPending() throws IOException {
        ResponseResult<BitgetOrdersPlanPendingResp> planResp = bitgetCustomService.getOrdersPlanPending(BG_PLAN_TYPE_PROFIT_LOSS, BG_PRODUCT_TYPE_USDT_FUTURES);
        System.out.println(JsonUtil.toJson(planResp));
    }

    @Test
    public void t3() throws IOException {
        ResponseResult<List<BitgetAccountsResp>> accounts = bitgetCustomService.getAccounts();
        System.out.println(JsonUtil.toJson(accounts));

    }

    public void calculateRangePrice(List<BitgetMixMarketCandlesResp> candles, String symbol) {
        if (candles.isEmpty()) return;

        // 获取前10个上涨K线的最高价
        List<BitgetMixMarketCandlesResp> top10HighPrices = candles.stream()
                .filter(c -> gte(c.getClosePrice(), c.getOpenPrice()))
                .sorted(Comparator.comparing(BitgetMixMarketCandlesResp::getHighPrice).reversed())
                .limit(10).toList();

        // 获取前10个下跌K线的最低价
        List<BitgetMixMarketCandlesResp> top10LowPrices = candles.stream()
                .filter(c -> lte(c.getClosePrice(), c.getOpenPrice()))
                .sorted(Comparator.comparing(BitgetMixMarketCandlesResp::getLowPrice))
                .limit(10).toList();

        // 获取整体最高价和最低价K线
        BitgetMixMarketCandlesResp highPriceCandle = rangeTradingStrategyV5Service.findMaxHighCandle(candles);
        BitgetMixMarketCandlesResp lowPriceCandle = rangeTradingStrategyV5Service.findMinLowCandle(candles);

        if (highPriceCandle == null) return;

        // 计算关键价格指标
        BigDecimal highPrice = highPriceCandle.getHighPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal lowPrice = lowPriceCandle.getLowPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal averagePrice = highPrice.add(lowPrice).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

        // 计算前10高价的均价
        BigDecimal highPriceSum = top10HighPrices.stream().map(BitgetMixMarketCandlesResp::getHighPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal highPriceAvg = highPriceSum.divide(BigDecimal.valueOf(top10HighPrices.size()), 2, RoundingMode.HALF_UP);

        // 计算前10低价的均价
        BigDecimal lowPriceSum = top10LowPrices.stream().map(BitgetMixMarketCandlesResp::getLowPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal lowPriceAvg = lowPriceSum.divide(BigDecimal.valueOf(top10LowPrices.size()), 2, RoundingMode.HALF_UP);

        Long highPriceTimestamp240 = highPriceCandle.getTimestamp();
        Long lowPriceTimestamp240 = lowPriceCandle.getTimestamp();
        if (candles.size() > 240) {
            List<BitgetMixMarketCandlesResp> defaultCandles = candles.subList(candles.size() - 240, candles.size());
            BitgetMixMarketCandlesResp defaultHigh = rangeTradingStrategyV5Service.findMaxHighCandle(defaultCandles);
            BitgetMixMarketCandlesResp defaultLow = rangeTradingStrategyV5Service.findMinLowCandle(defaultCandles);
            highPriceTimestamp240 = defaultHigh != null ? defaultHigh.getTimestamp() : highPriceTimestamp240;
            lowPriceTimestamp240 = defaultLow != null ? defaultLow.getTimestamp() : lowPriceTimestamp240;
        }

        System.out.println("币种: " + symbol + " 区间数量: " + candles.size());
        System.out.println("最高均价: " + highPriceAvg + " 最低均价: " + lowPriceAvg);
        System.out.println("最高价: " + highPrice + " 时间: " + DateUtil.formatDateTime(new Date(highPriceCandle.getTimestamp())));
        System.out.println("均价: " + averagePrice);
        System.out.println("最低价: " + lowPrice + " 时间: " + DateUtil.formatDateTime(new Date(lowPriceCandle.getTimestamp())));
        System.out.println("240K线最高价时间: " + DateUtil.formatDateTime(new Date(highPriceTimestamp240)));
        System.out.println("240K线最低价时间: " + DateUtil.formatDateTime(new Date(lowPriceTimestamp240)));
        System.out.println("-----------------------------------");
    }


    @Test
    public void t4() throws IOException {
        String[] symbols = new String[]{"BTCUSDT", "ETHUSDT", "XRPUSDT", "SOLUSDT"};
        symbols = new String[]{"BTCUSDT", "ETHUSDT"};
        String startTime = null; // 开始时间
        String endTime = null;//String.valueOf(DateUtil.parseDateTime("2025-07-29 19:00:00").toTimestamp().getTime());
        String granularity = "1H"; // 1小时K线
        Integer limit = 1000; // 获取1000根K线数据
        for (String symbol : symbols) {
            ResponseResult<List<BitgetMixMarketCandlesResp>> rs = bitgetCustomService.getMinMarketCandles(symbol, BG_PRODUCT_TYPE_USDT_FUTURES, granularity, limit, startTime, endTime);
            List<BitgetMixMarketCandlesResp> candles = rs.getData();
            candles = rangeTradingStrategyV5Service.calculateValidRangeSize(candles);
            calculateRangePrice(candles, symbol);
        }
    }


}
