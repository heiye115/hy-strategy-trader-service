package com.hy;

import cn.hutool.core.date.DateUtil;
import com.bitget.custom.entity.BitgetAllPositionResp;
import com.bitget.custom.entity.BitgetMixMarketCandlesResp;
import com.bitget.custom.entity.BitgetOrderDetailResp;
import com.bitget.custom.entity.BitgetOrdersPlanPendingResp;
import com.bitget.openapi.dto.response.ResponseResult;
import com.hy.common.service.BitgetCustomService;
import com.hy.common.utils.json.JsonUtil;
import com.hy.modules.contract.entity.CandlesDate;
import com.hy.modules.contract.service.RangeTradingStrategyV6Service;
import com.hy.modules.contract.service.RangeTradingStrategyV7Service;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static com.hy.common.constants.BitgetConstant.*;
import static com.hy.common.utils.num.BigDecimalUtils.gt;
import static com.hy.common.utils.num.BigDecimalUtils.lt;

@SpringBootTest
class ShortTermTradingStrategyV1ServiceTests {


    @Autowired
    BitgetCustomService bitgetCustomService;

    @Autowired
    RangeTradingStrategyV7Service rangeTradingStrategyV7Service;

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
        rangeTradingStrategyV7Service.startHistoricalKlineMonitoring();
    }

    @Test
    public void getAllPosition() throws IOException {
        // 获取当前持仓
        ResponseResult<List<BitgetAllPositionResp>> positionResp = bitgetCustomService.getAllPosition();
        if (!BG_RESPONSE_CODE_SUCCESS.equals(positionResp.getCode())) {
            return;
        }
        List<BitgetAllPositionResp> positions = Optional.ofNullable(positionResp.getData()).orElse(Collections.emptyList());
        Map<String, BitgetAllPositionResp> positionMap = positions.stream().collect(Collectors.toMap(BitgetAllPositionResp::getSymbol, p -> p, (existing, replacement) -> existing));
        System.out.println("当前持仓: " + JsonUtil.toJson(positionMap));
    }

    @Test
    public void getOrdersPlanPending() throws IOException {
        ResponseResult<BitgetOrdersPlanPendingResp> planResp = bitgetCustomService.getOrdersPlanPending(BG_PLAN_TYPE_PROFIT_LOSS, BG_PRODUCT_TYPE_USDT_FUTURES);
        System.out.println(JsonUtil.toJson(planResp));
    }

    @Test
    public void t3() throws IOException {
        ResponseResult<BitgetOrderDetailResp> orderDetailResult = bitgetCustomService.getOrderDetail("BTCUSDT", "1346526727812231168");
        System.out.println(JsonUtil.toJson(orderDetailResult));
    }

    public void calculateRangePrice(List<BitgetMixMarketCandlesResp> candles, String symbol) {
        if (candles.isEmpty()) return;
        //获取前10最高价,从阴线(最低价)中获取
        List<BitgetMixMarketCandlesResp> top10HighPrices = candles.stream()
                .filter(c -> lt(c.getClosePrice(), c.getOpenPrice()))
                .sorted(Comparator.comparing(BitgetMixMarketCandlesResp::getLowPrice).reversed())
                .limit(10).toList();
        //top10HighPrices按时间排序
        //top10HighPrices = top10HighPrices.stream().sorted(Comparator.comparing(BitgetMixMarketCandlesResp::getTimestamp)).toList();
        //top10HighPrices.forEach(c -> System.out.println("上涨K线时间: " + DateUtil.formatDateTime(new Date(c.getTimestamp())) + " 低价: " + c.getLowPrice() + " 开盘价: " + c.getOpenPrice() + " 收盘价: " + c.getClosePrice()));


        //获取前10最低价,从阳线(最高价)中获取
        List<BitgetMixMarketCandlesResp> top10LowPrices = candles.stream()
                .filter(c -> gt(c.getClosePrice(), c.getOpenPrice()))
                .sorted(Comparator.comparing(BitgetMixMarketCandlesResp::getHighPrice))
                .limit(10).toList();
        //top10LowPrices按时间排序
        //top10LowPrices = top10LowPrices.stream().sorted(Comparator.comparing(BitgetMixMarketCandlesResp::getTimestamp)).toList();
        //top10LowPrices.forEach(c -> System.out.println("下跌K线时间: " + DateUtil.formatDateTime(new Date(c.getTimestamp())) + " 高价: " + c.getHighPrice() + " 开盘价: " + c.getOpenPrice() + " 收盘价: " + c.getClosePrice()));

        BitgetMixMarketCandlesResp highPriceCandle = rangeTradingStrategyV7Service.findMaxHighCandle(candles);
        BitgetMixMarketCandlesResp lowPriceCandle = rangeTradingStrategyV7Service.findMinLowCandle(candles);

        // 计算前10高价的均价
        BigDecimal highPriceSum = top10HighPrices.stream().map(BitgetMixMarketCandlesResp::getLowPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal highPriceAvg = highPriceSum.divide(BigDecimal.valueOf(top10HighPrices.size()), 4, RoundingMode.HALF_UP);


        // 计算前10低价的均价
        BigDecimal lowPriceSum = top10LowPrices.stream().map(BitgetMixMarketCandlesResp::getHighPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal lowPriceAvg = lowPriceSum.divide(BigDecimal.valueOf(top10LowPrices.size()), 4, RoundingMode.HALF_UP);

        System.out.println("币种: " + symbol + " 区间数量: " + candles.size());
        System.out.println("最高均价: " + highPriceAvg + " 最低均价: " + lowPriceAvg);
        System.out.println("最高价: " + highPriceCandle.getHighPrice() + " 最高价K线时间: " + DateUtil.formatDateTime(new Date(highPriceCandle.getTimestamp())) + " 最低价: " + lowPriceCandle.getLowPrice() + " 最低价K线时间: " + DateUtil.formatDateTime(new Date(lowPriceCandle.getTimestamp())));
        System.out.println("-----------------------------------");
    }

    @Test
    public void t4() throws IOException, InterruptedException {
        String[] symbols = new String[]{"BTCUSDT", "ETHUSDT", "XRPUSDT", "SOLUSDT"};
        symbols = new String[]{"BTCUSDT", "ETHUSDT"};
        String startTime = null; // 开始时间
        String endTime = null;//String.valueOf(DateUtil.parseDateTime("2025-07-29 19:00:00").toTimestamp().getTime());
        String granularity = "5m"; // 5分钟K线
        Integer limit = 300; // 获取300根K线数据
        for (String symbol : symbols) {
            ResponseResult<List<BitgetMixMarketCandlesResp>> rs = bitgetCustomService.getMinMarketCandles(symbol, BG_PRODUCT_TYPE_USDT_FUTURES, granularity, limit, startTime, endTime);
            List<BitgetMixMarketCandlesResp> candles = rs.getData();
            calculateRangePrice(candles, symbol);
        }

    }
}
