package com.hy;

import cn.hutool.core.date.DateUtil;
import com.bitget.custom.entity.BitgetMixMarketCandlesResp;
import com.bitget.custom.entity.BitgetOrdersPlanPendingResp;
import com.bitget.openapi.dto.response.ResponseResult;
import com.hy.common.service.BitgetOldCustomService;
import com.hy.common.utils.json.JsonUtil;
import com.hy.modules.contract.entity.CandlesDate;
import com.hy.modules.contract.entity.RangePriceStrategyConfig;
import com.hy.modules.contract.service.RangeTradingStrategyV6Service;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static com.hy.common.constants.BitgetConstant.BG_PLAN_TYPE_PROFIT_LOSS;
import static com.hy.common.constants.BitgetConstant.BG_PRODUCT_TYPE_USDT_FUTURES;
import static com.hy.common.utils.num.BigDecimalUtils.gte;
import static com.hy.common.utils.num.BigDecimalUtils.lte;
import static com.hy.modules.contract.service.RangeTradingStrategyV6Service.STRATEGY_CONFIG_MAP;
import static com.hy.modules.contract.service.RangeTradingStrategyV6Service.distinctAndSortByTimestamp;

@SpringBootTest
class RangeTradingStrategyV6ServiceTests {


    @Autowired
    BitgetOldCustomService bitgetCustomService;

    @Autowired
    RangeTradingStrategyV6Service rangeTradingStrategyV6Service;

    public static void main(String[] args) {
        List<CandlesDate> candlesDate = RangeTradingStrategyV6Service.getCandlesDate(6, 200);
        for (CandlesDate date : candlesDate) {
            System.out.println("开始时间: " + DateUtil.formatDateTime(new Date(date.getStartTime())) + " -> " + date.getStartTime() + " 结束时间: " + DateUtil.formatDateTime(new Date(date.getEndTime())) + " -> " + date.getEndTime());
        }
    }


    @Test
    public void t0() throws IOException {
        List<CandlesDate> candlesDate = RangeTradingStrategyV6Service.getCandlesDate(6, 200);
        List<BitgetMixMarketCandlesResp> candles = new ArrayList<>();
        for (CandlesDate date : candlesDate) {
            ResponseResult<List<BitgetMixMarketCandlesResp>> btcusdt = bitgetCustomService.getMixMarketHistoryCandles("BTCUSDT", "USDT-FUTURES", "1H", 200, date.getStartTime().toString(), date.getEndTime().toString());
            //System.out.println("BTCUSDT K线数据: " + JsonUtil.toJson(btcusdt));
            candles.addAll(btcusdt.getData());
        }
        for (BitgetMixMarketCandlesResp candle : candles) {
            System.out.println("K线数据: " + DateUtil.formatDateTime(new Date(candle.getTimestamp())) + " 开盘价: " + candle.getOpenPrice() + " 最高价: " + candle.getHighPrice() + " 最低价: " + candle.getLowPrice() + " 收盘价: " + candle.getClosePrice());
        }
    }

    @Test
    public void t1() throws IOException, InterruptedException {
        rangeTradingStrategyV6Service.startHistoricalKlineMonitoring();
    }

    @Test
    public void getAllPosition() throws IOException {

    }

    @Test
    public void getOrdersPlanPending() throws IOException {
        ResponseResult<BitgetOrdersPlanPendingResp> planResp = bitgetCustomService.getOrdersPlanPending(BG_PLAN_TYPE_PROFIT_LOSS, BG_PRODUCT_TYPE_USDT_FUTURES);
        System.out.println(JsonUtil.toJson(planResp));
    }

    @Test
    public void t3() throws IOException {

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
        BitgetMixMarketCandlesResp highPriceCandle = rangeTradingStrategyV6Service.findMaxHighCandle(candles);
        BitgetMixMarketCandlesResp lowPriceCandle = rangeTradingStrategyV6Service.findMinLowCandle(candles);

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
            BitgetMixMarketCandlesResp defaultHigh = rangeTradingStrategyV6Service.findMaxHighCandle(defaultCandles);
            BitgetMixMarketCandlesResp defaultLow = rangeTradingStrategyV6Service.findMinLowCandle(defaultCandles);
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


    public Map<String, List<BitgetMixMarketCandlesResp>> historicalKlineMonitoring() {
        Map<String, List<BitgetMixMarketCandlesResp>> map = new HashMap<>();
        // 获取过去6个月，每段200小时的时间段
        List<CandlesDate> candlesDate = RangeTradingStrategyV6Service.getCandlesDate(6, 200);
        for (RangePriceStrategyConfig config : STRATEGY_CONFIG_MAP.values()) {
            List<BitgetMixMarketCandlesResp> candles = new ArrayList<>();
            for (CandlesDate date : candlesDate) {
                try {
                    // 获取K线数据
                    ResponseResult<List<BitgetMixMarketCandlesResp>> rs = bitgetCustomService.getMixMarketHistoryCandles(
                            config.getSymbol(),
                            BG_PRODUCT_TYPE_USDT_FUTURES,
                            config.getGranularity().getCode(),
                            200,
                            date.getStartTime().toString(),
                            date.getEndTime().toString());

                    candles.addAll(rs.getData());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (!candles.isEmpty()) {
                map.put(config.getSymbol(), distinctAndSortByTimestamp(candles));
            }

        }
        return map;
    }

    @Test
    public void t4() throws IOException, InterruptedException {
        Map<String, List<BitgetMixMarketCandlesResp>> listMap = historicalKlineMonitoring();
        String[] symbols = new String[]{"BTCUSDT", "ETHUSDT", "XRPUSDT", "SOLUSDT"};
        symbols = new String[]{"BTCUSDT", "ETHUSDT"};
        String startTime = null; // 开始时间
        String endTime = null;//String.valueOf(DateUtil.parseDateTime("2025-07-29 19:00:00").toTimestamp().getTime());
        String granularity = "1H"; // 1小时K线
        Integer limit = 500; // 获取500根K线数据
        for (String symbol : symbols) {
            List<BitgetMixMarketCandlesResp> historicalKlineCache = listMap.get(symbol);
            ResponseResult<List<BitgetMixMarketCandlesResp>> rs = bitgetCustomService.getMinMarketCandles(symbol, BG_PRODUCT_TYPE_USDT_FUTURES, granularity, limit, startTime, endTime);
            List<BitgetMixMarketCandlesResp> candles = rs.getData();
            historicalKlineCache.addAll(candles);
            historicalKlineCache = distinctAndSortByTimestamp(historicalKlineCache);
            candles = rangeTradingStrategyV6Service.calculateValidRangeSize(historicalKlineCache);
            calculateRangePrice(candles, symbol);
        }

    }
}
