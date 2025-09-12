package com.hy.modules.contract.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.bitget.custom.entity.*;
import com.bitget.openapi.dto.request.ws.SubscribeReq;
import com.bitget.openapi.dto.response.ResponseResult;
import com.hy.common.enums.BitgetEnum;
import com.hy.common.enums.SymbolEnum;
import com.hy.common.service.BitgetOldCustomService;
import com.hy.common.service.MailService;
import com.hy.common.utils.json.JsonUtil;
import com.hy.modules.contract.entity.RangePrice;
import com.hy.modules.contract.entity.RangePriceOrder;
import com.hy.modules.contract.entity.RangePricePlaceOrderParam;
import com.hy.modules.contract.entity.RangePriceStrategyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.hy.common.constants.BitgetConstant.*;
import static com.hy.common.utils.num.BigDecimalUtils.*;

/**
 * 区间交易策略服务类 V5
 * 实现基于价格区间的自动化交易策略
 * <p>
 * 主要功能：
 * 1. K线数据监控和区间价格计算
 * 2. 实时行情数据监控
 * 3. 策略信号生成和订单执行
 * 4. 仓位管理和风险控制
 * 5. 止盈止损管理
 */
@Slf4j
//@Service
public class RangeTradingStrategyV5Service {

    // ==================== 依赖注入 ====================

    /**
     * Bitget API服务
     */
    private final BitgetOldCustomService bitgetCustomService;

    /**
     * 邮件通知服务
     */
    private final MailService mailService;

    /**
     * 异步任务执行器
     */
    private final TaskExecutor taskExecutor;

    // ==================== 缓存和队列 ====================

    /**
     * 区间价格缓存 - 存储各币种的区间价格信息
     */
    private final static Map<String, RangePrice> RANGE_PRICE_CACHE = new ConcurrentHashMap<>();

    /**
     * 实时行情数据缓存 - 存储各币种的最新价格
     */
    private final static Map<String, BigDecimal> MARKET_PRICE_CACHE = new ConcurrentHashMap<>();

    /**
     * 订单队列 - 存储待执行的订单参数
     */
    private static final BlockingQueue<RangePricePlaceOrderParam> ORDER_QUEUE = new LinkedBlockingQueue<>(1000);

    // ==================== 控制标志 ====================

    /**
     * 订单消费者启动标志 - 确保只启动一次
     */
    private final AtomicBoolean ORDER_CONSUMER_STARTED = new AtomicBoolean(false);

    // ==================== 常量配置 ====================

    /**
     * K线数据获取数量限制
     */
    private final static Integer KLINE_DATA_LIMIT = 1000;

    /**
     * 延迟开单时间（毫秒）- 1小时
     */
    private final static Long DELAY_OPEN_TIME_MS = 3600000L;

    /**
     * 价格波动容忍度 - 0.05%
     */
    private final static BigDecimal PRICE_TOLERANCE_UPPER = new BigDecimal("1.0005");
    private final static BigDecimal PRICE_TOLERANCE_LOWER = new BigDecimal("0.9995");

    /**
     * 止损价格调整系数
     */
    private final static BigDecimal STOP_LOSS_UPPER_MULTIPLIER = new BigDecimal("1.001");
    private final static BigDecimal STOP_LOSS_LOWER_MULTIPLIER = new BigDecimal("0.999");

    /**
     * 开仓资金比例 - 20%
     */
    private final static BigDecimal OPEN_POSITION_RATIO = new BigDecimal("0.2");

    /**
     * 邮件接收地址
     */
    @Value("${spring.mail.username}")
    private String emailRecipient;

    /**
     * 是否允许增加杠杆
     */
    @Value("${strategy.leverage-increase}")
    private boolean leverageIncrease;

    // ==================== 策略配置 ====================

    /**
     * 策略配置映射 - 存储各币种的交易策略参数
     */
    private final static Map<String, RangePriceStrategyConfig> STRATEGY_CONFIG_MAP = new ConcurrentHashMap<>() {
        {
            // BTC配置：杠杆10倍，开仓金额50USDT，价格精度4位，数量精度1位
            put(SymbolEnum.BTCUSDT.getCode(), new RangePriceStrategyConfig(true, SymbolEnum.BTCUSDT.getCode(), 10, BigDecimal.valueOf(50.0), 4, 1, BitgetEnum.H1, 50.0));
            // ETH配置：杠杆2倍，开仓金额50USDT，价格精度2位，数量精度2位
            put(SymbolEnum.ETHUSDT.getCode(), new RangePriceStrategyConfig(true, SymbolEnum.ETHUSDT.getCode(), 2, BigDecimal.valueOf(50.0), 2, 2, BitgetEnum.H1, 50.0));
            // XRP配置：杠杆2倍，开仓金额50USDT，价格精度0位，数量精度4位
            //put(SymbolEnum.XRPUSDT.getCode(), new RangePriceStrategyConfig(true, SymbolEnum.XRPUSDT.getCode(), 2, BigDecimal.valueOf(50.0), 0, 4, BitgetEnum.H1, 50.0));
            // SOL配置：杠杆2倍，开仓金额50USDT，价格精度1位，数量精度3位
            //put(SymbolEnum.SOLUSDT.getCode(), new RangePriceStrategyConfig(true, SymbolEnum.SOLUSDT.getCode(), 2, BigDecimal.valueOf(50.0), 1, 3, BitgetEnum.H1, 50.0));
        }
    };

    /**
     * 延迟开单时间映射 - 控制各币种的开单频率
     */
    private final static Map<String, Long> DELAY_OPEN_TIME_MAP = STRATEGY_CONFIG_MAP.values().stream()
            .collect(Collectors.toMap(RangePriceStrategyConfig::getSymbol, v -> 0L));

    public RangeTradingStrategyV5Service(BitgetOldCustomService bitgetCustomService, MailService mailService, @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.bitgetCustomService = bitgetCustomService;
        this.mailService = mailService;
        this.taskExecutor = executor;
    }

    /**
     * 启动区间交易策略服务
     * 初始化账户配置、启动订单消费者、建立WebSocket连接
     */
    public void start() {
        // 初始化Bitget账户配置
        initializeBitgetAccount();
        // 启动订单消费者线程
        startOrderConsumer();
        // 建立WebSocket行情数据监控
        startWebSocketMarketDataMonitoring();
        log.info("区间交易策略服务启动完成, 当前配置: {}", JsonUtil.toJson(STRATEGY_CONFIG_MAP));
    }

    /**
     * 初始化Bitget账户配置
     * 设置杠杆、持仓模式和保证金模式等基础交易参数
     */
    public void initializeBitgetAccount() {
        try {
            for (RangePriceStrategyConfig config : STRATEGY_CONFIG_MAP.values()) {
                if (!config.getEnable()) continue;

                // 设置杠杆倍数
                calculateAndSetLeverage(config.getSymbol(), config.getLeverage());

                // 设置保证金模式为全仓
                setMarginModeForSymbol(config);
            }

            // 设置持仓模式为单向持仓
            setPositionMode();
        } catch (Exception e) {
            log.error("initializeBitgetAccount-error:", e);
        }
    }

    /**
     * 为指定币种设置杠杆倍数
     */
    private void setLeverageForSymbol(String symbol, Integer leverage) {
        try {
            ResponseResult<BitgetSetLeverageResp> rs = bitgetCustomService.setLeverage(
                    symbol, BG_PRODUCT_TYPE_USDT_FUTURES, DEFAULT_CURRENCY_USDT, leverage.toString(), null
            );
            log.info("setLeverageForSymbol-设置杠杆成功: symbol={}, leverage={}, result={}", symbol, leverage, JsonUtil.toJson(rs));
        } catch (Exception e) {
            log.error("setLeverageForSymbol-设置杠杆失败: symbol={}", symbol, e);
        }
    }

    /**
     * 为指定币种设置保证金模式
     */
    private void setMarginModeForSymbol(RangePriceStrategyConfig config) {
        try {
            ResponseResult<BitgetSetMarginModeResp> rs = bitgetCustomService.setMarginMode(
                    config.getSymbol(), BG_PRODUCT_TYPE_USDT_FUTURES, DEFAULT_CURRENCY_USDT, BG_MARGIN_MODE_CROSSED
            );
            log.info("setMarginModeForSymbol-设置保证金模式成功: symbol={}, result={}", config.getSymbol(), JsonUtil.toJson(rs));
        } catch (Exception e) {
            log.error("setMarginModeForSymbol-设置保证金模式失败: symbol={}", config.getSymbol(), e);
        }
    }

    /**
     * 设置持仓模式为单向持仓
     */
    private void setPositionMode() {
        try {
            ResponseResult<BitgetSetPositionModeResp> rs = bitgetCustomService.setPositionMode(
                    BG_PRODUCT_TYPE_USDT_FUTURES, BG_POS_MODE_ONE_WAY_MODE
            );
            log.info("setPositionMode-设置持仓模式成功: result={}", JsonUtil.toJson(rs));
        } catch (Exception e) {
            log.error("setPositionMode-设置持仓模式失败:", e);
        }
    }

    /**
     * 启动K线数据监控
     * 为每个启用的币种异步获取K线数据并计算区间价格
     */
    public void startKlineMonitoring() {
        for (RangePriceStrategyConfig config : STRATEGY_CONFIG_MAP.values()) {
            taskExecutor.execute(() -> {
                try {
                    // 获取K线数据
                    ResponseResult<List<BitgetMixMarketCandlesResp>> rs = bitgetCustomService.getMinMarketCandles(
                            config.getSymbol(), BG_PRODUCT_TYPE_USDT_FUTURES, config.getGranularity().getCode(), KLINE_DATA_LIMIT
                    );
                    if (!BG_RESPONSE_CODE_SUCCESS.equals(rs.getCode()) || rs.getData().isEmpty()) {
                        log.error("startKlineMonitoring-error: 获取K线数据失败, symbol: {}, rs: {}", config.getSymbol(), JsonUtil.toJson(rs));
                        return;
                    }

                    List<BitgetMixMarketCandlesResp> candlesData = rs.getData();
                    // 计算有效区间大小
                    List<BitgetMixMarketCandlesResp> validCandles = calculateValidRangeSize(candlesData);
                    // 计算区间价格
                    calculateRangePrice(validCandles, config);
                } catch (Exception e) {
                    log.error("startKlineMonitoring-error: symbol={}", config.getSymbol(), e);
                }
            });
        }
    }


    /**
     * 计算有效的区间大小
     * 根据K线数据计算合适的交易区间范围
     *
     * @param candles K线数据列表
     * @return 经过筛选的有效K线数据
     */
    public List<BitgetMixMarketCandlesResp> calculateValidRangeSize(List<BitgetMixMarketCandlesResp> candles) {
        int size = candles.size();
        if (size < KLINE_DATA_LIMIT) {
            log.warn("calculateValidRangeSize: K线数据不足{}条, 当前仅有{}条", KLINE_DATA_LIMIT, size);
            return candles;
        }

        // 取末尾240根K线
        List<BitgetMixMarketCandlesResp> defaultCandles = candles.subList(size - 240, size);
        BitgetMixMarketCandlesResp defaultHigh = findMaxHighCandle(defaultCandles);
        BitgetMixMarketCandlesResp defaultLow = findMinLowCandle(defaultCandles);
        if (defaultHigh == null || defaultLow == null) return defaultCandles;

        int cutHighIndex = -1;
        int cutLowIndex = -1;

        for (int i = size - 1; i >= 0; i--) {
            BitgetMixMarketCandlesResp c = candles.get(i);

            // 找突破最高点的蜡烛
            if (cutHighIndex == -1 &&
                    gt(c.getHighPrice(), defaultHigh.getHighPrice()) &&
                    gte(c.getClosePrice(), c.getOpenPrice()) &&
                    c.getTimestamp() < defaultHigh.getTimestamp()) {
                cutHighIndex = i;
            }

            // 找跌破最低点的蜡烛
            if (cutLowIndex == -1 &&
                    lt(c.getLowPrice(), defaultLow.getLowPrice()) &&
                    lte(c.getClosePrice(), c.getOpenPrice()) &&
                    c.getTimestamp() < defaultLow.getTimestamp()) {
                cutLowIndex = i;
            }

            // 如果都找到了，就不用再往前循环
            if (cutHighIndex != -1 && cutLowIndex != -1) break;
        }

        List<BitgetMixMarketCandlesResp> highCandles =
                (cutHighIndex == -1) ? defaultCandles : candles.subList(cutHighIndex, size);

        List<BitgetMixMarketCandlesResp> lowCandles =
                (cutLowIndex == -1) ? defaultCandles : candles.subList(cutLowIndex, size);

        return highCandles.size() > lowCandles.size() ? highCandles : lowCandles;
    }

    /**
     * 查找最高价K线
     */
    public BitgetMixMarketCandlesResp findMaxHighCandle(List<BitgetMixMarketCandlesResp> list) {
        return list.stream().max(Comparator.comparing(BitgetMixMarketCandlesResp::getHighPrice)).orElse(null);
    }

    /**
     * 查找最低价K线
     */
    public BitgetMixMarketCandlesResp findMinLowCandle(List<BitgetMixMarketCandlesResp> list) {
        return list.stream().min(Comparator.comparing(BitgetMixMarketCandlesResp::getLowPrice)).orElse(null);
    }

    /**
     * 计算区间价格
     * 根据K线数据计算最高价、最低价、均价等关键价格指标
     *
     * @param candles K线数据列表
     * @param config  策略配置
     */
    public void calculateRangePrice(List<BitgetMixMarketCandlesResp> candles, RangePriceStrategyConfig config) {
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
        BitgetMixMarketCandlesResp highPriceCandle = findMaxHighCandle(candles);
        BitgetMixMarketCandlesResp lowPriceCandle = findMinLowCandle(candles);
        if (highPriceCandle == null) return;

        // 计算关键价格指标
        BigDecimal highPrice = highPriceCandle.getHighPrice().setScale(config.getPricePlace(), RoundingMode.HALF_UP);
        BigDecimal lowPrice = lowPriceCandle.getLowPrice().setScale(config.getPricePlace(), RoundingMode.HALF_UP);
        BigDecimal averagePrice = highPrice.add(lowPrice).divide(BigDecimal.valueOf(2), config.getPricePlace(), RoundingMode.HALF_UP);

        // 计算前10高价的均价
        BigDecimal highPriceSum = top10HighPrices.stream().map(BitgetMixMarketCandlesResp::getHighPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal highPriceAvg = highPriceSum.divide(BigDecimal.valueOf(top10HighPrices.size()), config.getPricePlace(), RoundingMode.HALF_UP);

        // 计算前10低价的均价
        BigDecimal lowPriceSum = top10LowPrices.stream().map(BitgetMixMarketCandlesResp::getLowPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal lowPriceAvg = lowPriceSum.divide(BigDecimal.valueOf(top10LowPrices.size()), config.getPricePlace(), RoundingMode.HALF_UP);

        Long highPriceTimestamp240 = highPriceCandle.getTimestamp();
        Long lowPriceTimestamp240 = lowPriceCandle.getTimestamp();
        if (candles.size() > 240) {
            List<BitgetMixMarketCandlesResp> defaultCandles = candles.subList(candles.size() - 240, candles.size());
            BitgetMixMarketCandlesResp defaultHigh = findMaxHighCandle(defaultCandles);
            BitgetMixMarketCandlesResp defaultLow = findMinLowCandle(defaultCandles);
            highPriceTimestamp240 = defaultHigh != null ? defaultHigh.getTimestamp() : highPriceTimestamp240;
            lowPriceTimestamp240 = defaultLow != null ? defaultLow.getTimestamp() : lowPriceTimestamp240;
        }

        // 更新区间价格缓存
        RANGE_PRICE_CACHE.put(config.getSymbol(), new RangePrice(
                config.getSymbol(),
                highPriceCandle.getTimestamp(),
                highPrice,
                lowPriceCandle.getTimestamp(),
                lowPrice,
                averagePrice,
                highPriceAvg,
                lowPriceAvg,
                candles.size(),
                highPriceTimestamp240,
                lowPriceTimestamp240
        ));
    }

    /**
     * 启动行情数据监控
     * 通过REST API获取实时行情数据
     */
    public void startMarketDataMonitoring() {
        for (RangePriceStrategyConfig config : STRATEGY_CONFIG_MAP.values()) {
            taskExecutor.execute(() -> {
                try {
                    ResponseResult<List<BitgetMixMarketTickerResp>> rs = bitgetCustomService.getMixMarketTicker(config.getSymbol(), BG_PRODUCT_TYPE_USDT_FUTURES);
                    if (rs.getData() == null || rs.getData().isEmpty()) return;
                    MARKET_PRICE_CACHE.put(config.getSymbol(), new BigDecimal(rs.getData().getFirst().getLastPr()));
                } catch (Exception e) {
                    log.error("startMarketDataMonitoring-error: symbol={}", config.getSymbol(), e);
                }
            });
        }
    }

    /**
     * 策略信号监控
     * 根据区间价格和当前行情生成交易信号
     */
    public void monitorTradingSignals() {
        try {
            if (RANGE_PRICE_CACHE.isEmpty()) return;

            for (RangePrice rangePrice : RANGE_PRICE_CACHE.values()) {
                RangePriceStrategyConfig config = STRATEGY_CONFIG_MAP.get(rangePrice.getSymbol());
                BitgetEnum granularity = config.getGranularity();
                long millis = granularity.getDuration().toMillis() * 2;
                long currentTime = System.currentTimeMillis();
                long highPriceTimestamp = rangePrice.getHighPriceTimestamp240() + millis;
                long lowPriceTimestamp = rangePrice.getLowPriceTimestamp240() + millis;

                // 如果当前时间在高价或低价的时间戳2倍的K线周期内，则不处理
                if (currentTime < highPriceTimestamp || currentTime < lowPriceTimestamp) continue;

                Long delay = DELAY_OPEN_TIME_MAP.get(rangePrice.getSymbol());
                if (currentTime < delay || !config.getEnable() || !MARKET_PRICE_CACHE.containsKey(rangePrice.getSymbol()))
                    continue;

                RangePricePlaceOrderParam order = generateOrderSignal(rangePrice, config.getPricePlace(), MARKET_PRICE_CACHE.get(rangePrice.getSymbol()));
                if (order == null) continue;

                if (ORDER_QUEUE.offer(order)) {
                    log.info("monitorTradingSignals: 队列添加订单成功, order: {}", JsonUtil.toJson(order));
                    DELAY_OPEN_TIME_MAP.put(rangePrice.getSymbol(), currentTime + DELAY_OPEN_TIME_MS); // 设置延迟开单时间
                }
            }
        } catch (Exception e) {
            log.error("monitorTradingSignals-error", e);
        }
    }

    /**
     * 生成订单信号
     * 根据当前价格和区间价格判断是否生成买卖信号
     *
     * @param pricePlace  价格精度
     * @param rangePrice  区间价格信息
     * @param latestPrice 最新价格
     * @return 订单参数，如果不满足条件则返回null
     */
    public RangePricePlaceOrderParam generateOrderSignal(RangePrice rangePrice, Integer pricePlace, BigDecimal latestPrice) {
        BigDecimal highPrice = rangePrice.getHighPrice();
        BigDecimal lowPrice = rangePrice.getLowPrice();
        BigDecimal averagePrice = rangePrice.getAveragePrice();
        BigDecimal highAveragePrice = rangePrice.getHighAveragePrice();
        BigDecimal lowAveragePrice = rangePrice.getLowAveragePrice();
        BigDecimal value100 = BigDecimal.valueOf(100);
        BigDecimal value2 = BigDecimal.TWO;

        // 计算价格容忍区间
        BigDecimal upHighPrice = highAveragePrice.multiply(PRICE_TOLERANCE_UPPER).setScale(pricePlace, RoundingMode.HALF_UP);
        BigDecimal downHighPrice = highAveragePrice.multiply(PRICE_TOLERANCE_LOWER).setScale(pricePlace, RoundingMode.HALF_UP);
        BigDecimal upLowPrice = lowAveragePrice.multiply(PRICE_TOLERANCE_UPPER).setScale(pricePlace, RoundingMode.HALF_UP);
        BigDecimal downLowPrice = lowAveragePrice.multiply(PRICE_TOLERANCE_LOWER).setScale(pricePlace, RoundingMode.HALF_UP);

        RangePricePlaceOrderParam order = new RangePricePlaceOrderParam();
        order.setClientOid(IdUtil.getSnowflakeNextIdStr());
        order.setSymbol(rangePrice.getSymbol());
        order.setPrice(latestPrice);
        order.setOrderType(BG_ORDER_TYPE_MARKET);
        order.setMarginMode(BG_MARGIN_MODE_CROSSED);
        order.setPresetStopSurplusPrice1(averagePrice.setScale(pricePlace, RoundingMode.HALF_UP));

        // 判断是否在卖出区间
        if (gte(latestPrice, downHighPrice) && lte(latestPrice, upHighPrice)) {
            BigDecimal highDiffPercent = highPrice.subtract(highAveragePrice).divide(highAveragePrice, 10, RoundingMode.HALF_UP)
                    .multiply(value100).abs().setScale(2, RoundingMode.HALF_UP);
            //最高均价和最高价必须在2%以内
            if (gt(highDiffPercent, value2)) return null;

            order.setSide(BG_SIDE_SELL);
            order.setPresetStopLossPrice(highPrice.multiply(STOP_LOSS_UPPER_MULTIPLIER).setScale(pricePlace, RoundingMode.HALF_UP));
            order.setPresetStopSurplusPrice2(lowAveragePrice.setScale(pricePlace, RoundingMode.HALF_UP));
            return lt(latestPrice, averagePrice) || lt(latestPrice, order.getPresetStopSurplusPrice2()) ? null : order;
        }

        // 判断是否在买入区间
        if (gte(latestPrice, downLowPrice) && lte(latestPrice, upLowPrice)) {
            BigDecimal lowDiffPercent = lowPrice.subtract(lowAveragePrice).divide(lowAveragePrice, 10, RoundingMode.HALF_UP)
                    .multiply(value100).abs().setScale(2, RoundingMode.HALF_UP);
            //最低均价和最低价必须在2%以内
            if (gt(lowDiffPercent, value2)) return null;


            order.setSide(BG_SIDE_BUY);
            order.setPresetStopLossPrice(lowPrice.multiply(STOP_LOSS_LOWER_MULTIPLIER).setScale(pricePlace, RoundingMode.HALF_UP));
            order.setPresetStopSurplusPrice2(highAveragePrice.setScale(pricePlace, RoundingMode.HALF_UP));
            return gt(latestPrice, averagePrice) || gt(latestPrice, order.getPresetStopSurplusPrice2()) ? null : order;
        }
        return null;
    }

    /**
     * 启动订单消费者
     * 从队列中获取订单并执行下单操作
     */
    public void startOrderConsumer() {
        if (ORDER_CONSUMER_STARTED.compareAndSet(false, true)) {
            taskExecutor.execute(() -> {
                while (true) {
                    try {
                        RangePricePlaceOrderParam orderParam = ORDER_QUEUE.take(); // 阻塞直到有数据

                        // 校验当前是否已有仓位
                        if (hasExistingPosition(orderParam.getSymbol())) continue;

                        // 校验账户余额
                        if (!validateAccountBalance(orderParam)) continue;

                        //计算并设置杠杆倍数
                        Integer leverage = calculateAndSetLeverage(orderParam.getSymbol());

                        // 计算开仓参数
                        calculateOrderParameters(orderParam, leverage);
                        log.info("startOrderConsumer: 准备下单，订单:{}", JsonUtil.toJson(orderParam));

                        // 执行下单
                        ResponseResult<BitgetPlaceOrderResp> orderResult = executeOrder(orderParam);
                        if (!BG_RESPONSE_CODE_SUCCESS.equals(orderResult.getCode()) || orderResult.getData() == null) {
                            log.error("startOrderConsumer: 下单失败，订单信息: {}, 错误信息: {}", JsonUtil.toJson(orderParam), JsonUtil.toJson(orderResult));
                            continue;
                        }

                        // 处理下单成功后的操作
                        handleSuccessfulOrder(orderParam, orderResult.getData());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("startOrderConsumer下单消费者线程被中断，准备退出", e);
                        break;
                    } catch (Exception e) {
                        log.error("startOrderConsumer异常：", e);
                    }
                }
            });
        }
    }

    /**
     * 计算并设置杠杆倍数
     **/
    public Integer calculateAndSetLeverage(String symbol) {
        RangePriceStrategyConfig config = STRATEGY_CONFIG_MAP.get(symbol);
        return calculateAndSetLeverage(symbol, config.getLeverage());
    }

    public Integer calculateAndSetLeverage(String symbol, Integer level) {
        Integer leverage = level;
        try {
            //是否允许增加杠杆
            if (leverageIncrease) {
                ResponseResult<List<BitgetHistoryPositionResp>> result = bitgetCustomService.getHistoryPosition(symbol, 100);
                if (!BG_RESPONSE_CODE_SUCCESS.equals(result.getCode()) || result.getData() == null || result.getData().isEmpty()) {
                    log.warn("calculateAndSetLeverage: 获取历史仓位失败，symbol: {}", symbol);
                    return leverage;
                }
                List<BitgetHistoryPositionResp> positions = result.getData();
                Map<String, List<BitgetHistoryPositionResp>> bhpMap = positions.stream().collect(Collectors.groupingBy(BitgetHistoryPositionResp::getSymbol));
                if (bhpMap.containsKey(symbol)) {
                    List<BitgetHistoryPositionResp> positionList = bhpMap.get(symbol);
                    positionList.sort(Comparator.comparing(BitgetHistoryPositionResp::getCtime).reversed());
                    for (BitgetHistoryPositionResp hp : positionList) {
                        // 如果当前仓位的盈亏小于等于0，则继续增加杠杆
                        if (gt(new BigDecimal(hp.getPnl()), BigDecimal.ZERO)) break;
                        leverage += 1;
                    }
                }
            }
            // 限制最大杠杆倍数为50
            if (leverage > 50) {
                leverage = 50;
            }
        } catch (Exception e) {
            log.error("calculateAndSetLeverage-error: symbol={}", symbol, e);
        }

        //设置杠杆倍数
        setLeverageForSymbol(symbol, leverage);
        return leverage;
    }

    /**
     * 检查是否已有仓位
     */
    private boolean hasExistingPosition(String symbol) throws Exception {
        List<BitgetAllPositionResp> positions = Optional.ofNullable(bitgetCustomService.getAllPosition().getData()).orElse(Collections.emptyList());
        return positions.stream().anyMatch(pos -> symbol.equals(pos.getSymbol()));
    }

    /**
     * 验证账户余额
     */
    private boolean validateAccountBalance(RangePricePlaceOrderParam orderParam) {
        Map<String, BitgetAccountsResp> accountMap = getAccountInfo();
        BitgetAccountsResp accountsResp = accountMap.get(DEFAULT_CURRENCY_USDT);
        if (accountsResp == null) {
            log.warn("validateAccountBalance: 未获取到USDT账户信息，无法执行下单! 订单: {}", JsonUtil.toJson(orderParam));
            return false;
        }

        RangePriceStrategyConfig config = STRATEGY_CONFIG_MAP.get(orderParam.getSymbol());
        BigDecimal available = new BigDecimal(accountsResp.getAvailable());
        BigDecimal crossedMaxAvailable = new BigDecimal(accountsResp.getCrossedMaxAvailable());
        BigDecimal openAmount = config.getOpenAmount();

        if (lt(available, openAmount) || lt(crossedMaxAvailable, openAmount)) {
            log.warn("validateAccountBalance: USDT账户可用余额不足，无法执行下单操作! 订单: {} 可用余额: {}, 全仓最大可用来开仓余额: {}", JsonUtil.toJson(orderParam), available, crossedMaxAvailable);
            return false;
        }
        return true;
    }

    /**
     * 计算订单参数
     */
    private void calculateOrderParameters(RangePricePlaceOrderParam orderParam, Integer leverage) {
        RangePriceStrategyConfig config = STRATEGY_CONFIG_MAP.get(orderParam.getSymbol());
        Map<String, BitgetAccountsResp> accountMap = getAccountInfo();
        BitgetAccountsResp accountsResp = accountMap.get(DEFAULT_CURRENCY_USDT);

        BigDecimal available = new BigDecimal(accountsResp.getAvailable());
        BigDecimal crossedMaxAvailable = new BigDecimal(accountsResp.getCrossedMaxAvailable());
        BigDecimal openAmount = config.getOpenAmount();

        // 计算开仓金额（取初始值或比例值）
        BigDecimal proportionAmount = available.multiply(OPEN_POSITION_RATIO).setScale(2, RoundingMode.HALF_UP);
        if (gt(proportionAmount, openAmount) && gte(crossedMaxAvailable, proportionAmount)) {
            openAmount = proportionAmount;
        }

        // 计算实际开仓数量
        BigDecimal realityOpenAmount = openAmount.multiply(BigDecimal.valueOf(leverage));
        BigDecimal size = realityOpenAmount.divide(orderParam.getPrice(), config.getVolumePlace(), RoundingMode.HALF_UP);
        orderParam.setSize(size.toPlainString());
    }

    /**
     * 执行下单操作
     */
    private ResponseResult<BitgetPlaceOrderResp> executeOrder(RangePricePlaceOrderParam orderParam) throws Exception {
        return bitgetCustomService.placeOrder(
                orderParam.getClientOid(),
                orderParam.getSymbol(),
                orderParam.getSize(),
                orderParam.getSide(),
                orderParam.getTradeSide(),
                orderParam.getOrderType(),
                orderParam.getMarginMode()
        );
    }

    /**
     * 处理下单成功后的操作
     */
    private void handleSuccessfulOrder(RangePricePlaceOrderParam orderParam, BitgetPlaceOrderResp orderResult) {
        RangePriceOrder order = BeanUtil.toBean(orderParam, RangePriceOrder.class);
        order.setOrderId(orderResult.getOrderId());
        order.setClientOid(orderResult.getClientOid());

        log.info("handleSuccessfulOrder: 下单成功，订单信息:{} , Bitget订单信息:{}", JsonUtil.toJson(orderParam), JsonUtil.toJson(order));

        // 设置延迟开单时间
        DELAY_OPEN_TIME_MAP.put(orderParam.getSymbol(), System.currentTimeMillis() + DELAY_OPEN_TIME_MS);

        // 设置止损
        setStopLossOrder(orderParam.getSymbol(), orderParam.getPresetStopLossPrice(), null, null, orderParam.getSide(), BG_PLAN_TYPE_POS_LOSS);

        // 设置分批止盈
        RangePriceStrategyConfig config = STRATEGY_CONFIG_MAP.get(orderParam.getSymbol());
        setBatchTakeProfitOrders(orderResult.getOrderId(), orderParam, config);
    }

    /**
     * 设置分批止盈订单
     */
    public void setBatchTakeProfitOrders(String orderId, RangePricePlaceOrderParam orderParam, RangePriceStrategyConfig config) {
        try {
            ResponseResult<BitgetOrderDetailResp> orderDetailResult = bitgetCustomService.getOrderDetail(orderParam.getSymbol(), orderId);
            if (!BG_RESPONSE_CODE_SUCCESS.equals(orderDetailResult.getCode()) || orderDetailResult.getData() == null) {
                log.error("setBatchTakeProfitOrders:获取订单详情失败，订单ID: {}, 错误信息: {}", orderId, JsonUtil.toJson(orderDetailResult));
                return;
            }

            BitgetOrderDetailResp orderDetail = orderDetailResult.getData();
            BigDecimal totalVolume = new BigDecimal(orderDetail.getBaseVolume());

            // 设置仓位止盈
            setStopLossOrder(orderParam.getSymbol(), orderParam.getPresetStopSurplusPrice2(), orderParam.getPresetStopSurplusPrice2(), null, orderParam.getSide(), BG_PLAN_TYPE_POS_PROFIT);

            // 设置分批止盈计划
            BigDecimal takeProfitPosition = totalVolume.multiply(BigDecimal.valueOf(config.getTakeProfitPositionPercent1() / 100.0)).setScale(config.getVolumePlace(), RoundingMode.HALF_UP);
            setStopLossOrder(orderParam.getSymbol(), orderParam.getPresetStopSurplusPrice1(), orderParam.getPresetStopSurplusPrice1(), takeProfitPosition, orderParam.getSide(), BG_PLAN_TYPE_PROFIT_PLAN);
        } catch (Exception e) {
            log.error("setBatchTakeProfitOrders-error: orderId={}", orderId, e);
        }
    }

    /**
     * 获取账户信息
     */
    public Map<String, BitgetAccountsResp> getAccountInfo() {
        try {
            ResponseResult<List<BitgetAccountsResp>> accountsResp = bitgetCustomService.getAccounts();
            if (accountsResp != null && BG_RESPONSE_CODE_SUCCESS.equals(accountsResp.getCode())) {
                List<BitgetAccountsResp> accounts = accountsResp.getData();
                if (accounts != null && !accounts.isEmpty()) {
                    return accounts.stream().collect(Collectors.toMap(BitgetAccountsResp::getMarginCoin, v -> v, (k1, k2) -> k1));
                }
            }
        } catch (Exception e) {
            log.error("getAccountInfo-error", e);
        }
        return new ConcurrentHashMap<>();
    }

    /**
     * 设置止盈止损委托计划
     */
    public void setStopLossOrder(String symbol, BigDecimal triggerPrice, BigDecimal executePrice, BigDecimal size, String holdSide, String planType) {
        BitgetPlaceTpslOrderParam param = new BitgetPlaceTpslOrderParam();
        param.setClientOid(IdUtil.getSnowflakeNextIdStr());
        param.setMarginCoin(DEFAULT_CURRENCY_USDT);
        param.setProductType(BG_PRODUCT_TYPE_USDT_FUTURES);
        param.setSymbol(symbol);
        param.setPlanType(planType);
        param.setTriggerType(BG_TRIGGER_TYPE_FILL_PRICE);
        param.setTriggerPrice(triggerPrice.toPlainString());

        if (executePrice != null) {
            param.setExecutePrice(executePrice.toPlainString());
        }

        if (size != null) {
            param.setSize(size.toPlainString());
        }

        param.setHoldSide(holdSide);

        try {
            ResponseResult<BitgetPlaceTpslOrderResp> rs = bitgetCustomService.placeTpslOrder(param);
            if (rs == null || !BG_RESPONSE_CODE_SUCCESS.equals(rs.getCode())) {
                log.error("setStopLossOrder: 设置止盈止损委托计划失败, param: {}", JsonUtil.toJson(param));
                return;
            }
            log.info("setStopLossOrder: 设置止盈止损委托计划成功, param: {}, result: {}", JsonUtil.toJson(param), JsonUtil.toJson(rs));
        } catch (Exception e) {
            log.error("setStopLossOrder-error: 设置止盈止损委托计划失败, param: {}, error: {}", JsonUtil.toJson(param), e.getMessage());
        }
    }

    /**
     * 仓位管理
     * 判断是否允许开单并动态调整止损计划单
     */
    public void managePositions() {
        try {
            // 获取当前持仓
            ResponseResult<List<BitgetAllPositionResp>> positionResp = bitgetCustomService.getAllPosition();
            if (!BG_RESPONSE_CODE_SUCCESS.equals(positionResp.getCode())) {
                log.error("managePositions-error: 获取仓位信息失败, rs: {}", JsonUtil.toJson(positionResp));
                return;
            }

            List<BitgetAllPositionResp> positions = Optional.ofNullable(positionResp.getData()).orElse(Collections.emptyList());
            Map<String, BitgetAllPositionResp> positionMap = positions.stream().collect(Collectors.toMap(BitgetAllPositionResp::getSymbol, p -> p, (existing, replacement) -> existing));
            // 如果有仓位，延迟开单时间设置为当前时间 + 1小时
            DELAY_OPEN_TIME_MAP.replaceAll((symbol, oldDelay) -> {
                if (positionMap.containsKey(symbol)) {
                    return System.currentTimeMillis() + DELAY_OPEN_TIME_MS;
                }
                return oldDelay;
            });

            // 必须有仓位才能执行后续操作
            if (positions.isEmpty()) return;

            // 获取当前计划止盈止损委托
            ResponseResult<BitgetOrdersPlanPendingResp> planResp = bitgetCustomService.getOrdersPlanPending(BG_PLAN_TYPE_PROFIT_LOSS, BG_PRODUCT_TYPE_USDT_FUTURES);
            if (!BG_RESPONSE_CODE_SUCCESS.equals(planResp.getCode())) {
                log.error("managePositions-error: 获取计划委托信息失败, rs: {}", JsonUtil.toJson(planResp));
                return;
            }
            BitgetOrdersPlanPendingResp plan = planResp.getData();
            if (plan == null || plan.getEntrustedList() == null || plan.getEntrustedList().isEmpty()) return;

            List<BitgetOrdersPlanPendingResp.EntrustedOrder> entrustedOrders = plan.getEntrustedList();

            Map<String, List<BitgetOrdersPlanPendingResp.EntrustedOrder>> entrustedOrdersMap = entrustedOrders.stream()
                    .collect(Collectors.groupingBy(BitgetOrdersPlanPendingResp.EntrustedOrder::getSymbol));
            // 更新止盈止损计划
            updateStopLossOrders(entrustedOrdersMap, positionMap);
        } catch (Exception e) {
            log.error("managePositions-error", e);
        }
    }

    /**
     * 更新止盈止损订单
     */
    private void updateStopLossOrders(Map<String, List<BitgetOrdersPlanPendingResp.EntrustedOrder>> entrustedOrdersMap, Map<String, BitgetAllPositionResp> positionMap) {
        if (entrustedOrdersMap == null || entrustedOrdersMap.isEmpty() || positionMap == null || positionMap.isEmpty()) {
            return;
        }
        try {
            positionMap.forEach((symbol, position) -> {
                List<BitgetOrdersPlanPendingResp.EntrustedOrder> orders = entrustedOrdersMap.get(symbol);
                if (orders == null || orders.isEmpty()) return;

                RangePrice rangePrice = RANGE_PRICE_CACHE.get(symbol);
                if (rangePrice == null) return;
                RangePriceStrategyConfig config = STRATEGY_CONFIG_MAP.get(symbol);
                if (config == null) return;
                BigDecimal averagePrice = rangePrice.getAveragePrice();
                BigDecimal highAveragePrice = rangePrice.getHighAveragePrice();
                BigDecimal lowAveragePrice = rangePrice.getLowAveragePrice();
                BigDecimal openPriceAvg = new BigDecimal(position.getOpenPriceAvg()).setScale(config.getPricePlace(), RoundingMode.HALF_UP);
                BigDecimal latestPrice = MARKET_PRICE_CACHE.get(symbol);

                for (BitgetOrdersPlanPendingResp.EntrustedOrder order : orders) {
                    BigDecimal triggerPrice = new BigDecimal(order.getTriggerPrice());
                    String planType = order.getPlanType();
                    String side = order.getSide();

                    //止盈计划
                    if (BG_PLAN_TYPE_PROFIT_PLAN.equals(planType)) {
                        if (ne(averagePrice, triggerPrice)) {
                            modifyStopLossOrder(order, averagePrice, averagePrice, order.getSize());
                        }
                    }
                    //仓位止盈
                    else if (BG_PLAN_TYPE_POS_PROFIT.equals(planType)) {
                        if (BG_SIDE_SELL.equals(side) && ne(highAveragePrice, triggerPrice)) {
                            modifyStopLossOrder(order, highAveragePrice, highAveragePrice, "");
                        } else if (BG_SIDE_BUY.equals(side) && ne(lowAveragePrice, triggerPrice)) {
                            modifyStopLossOrder(order, lowAveragePrice, lowAveragePrice, "");
                        }
                    }
                    //仓位止损
                    else if (BG_PLAN_TYPE_POS_LOSS.equals(planType) && latestPrice != null) {

                        //做多 sell 卖
                        if (BG_SIDE_SELL.equals(side) && gte(latestPrice, averagePrice)) {
                            BigDecimal newTriggerPrice = openPriceAvg.multiply(new BigDecimal("1.002")).setScale(config.getPricePlace(), RoundingMode.HALF_UP);
                            if (ne(triggerPrice, newTriggerPrice) && lt(newTriggerPrice, latestPrice)) {
                                modifyStopLossOrder(order, newTriggerPrice, null, "");
                            }
                        }
                        //做空 buy 买
                        else if (BG_SIDE_BUY.equals(side) && lte(latestPrice, averagePrice)) {
                            BigDecimal newTriggerPrice = openPriceAvg.multiply(new BigDecimal("0.998")).setScale(config.getPricePlace(), RoundingMode.HALF_UP);
                            if (ne(triggerPrice, newTriggerPrice) && gt(newTriggerPrice, latestPrice)) {
                                modifyStopLossOrder(order, newTriggerPrice, null, "");
                            }
                        }
                    }
                }
            });
        } catch (Exception e) {
            log.error("updateStopLossOrders-error: 更新止盈止损订单失败", e);
        }
    }


    /**
     * 修改止盈止损计划
     */
    private void modifyStopLossOrder(BitgetOrdersPlanPendingResp.EntrustedOrder order, BigDecimal newTriggerPrice, BigDecimal newExecutePrice, String size) {
        try {
            BitgetModifyTpslOrderParam param = new BitgetModifyTpslOrderParam();
            param.setOrderId(order.getOrderId());
            param.setMarginCoin(order.getMarginCoin());
            param.setProductType(BG_PRODUCT_TYPE_USDT_FUTURES);
            param.setSymbol(order.getSymbol());
            param.setTriggerPrice(newTriggerPrice.toPlainString());
            param.setTriggerType(BG_TRIGGER_TYPE_FILL_PRICE);

            if (newExecutePrice != null) {
                param.setExecutePrice(newExecutePrice.toPlainString());
            }

            param.setSize(size);
            ResponseResult<BitgetPlaceTpslOrderResp> result = bitgetCustomService.modifyTpslOrder(param);
            log.info("modifyStopLossOrder: 修改止盈止损计划成功, param: {}, result: {}", JsonUtil.toJson(param), JsonUtil.toJson(result));
        } catch (Exception e) {
            log.error("modifyStopLossOrder-error: 更新止盈止损计划失败, order: {}, newTriggerPrice: {}, error: {}", JsonUtil.toJson(order), newTriggerPrice, e.getMessage());
        }
    }

    /**
     * 启动WebSocket行情数据监控
     */
    public void startWebSocketMarketDataMonitoring() {
        List<SubscribeReq> subscribeRequests = new ArrayList<>();
        for (RangePriceStrategyConfig config : STRATEGY_CONFIG_MAP.values()) {
            subscribeRequests.add(SubscribeReq.builder()
                    .instType(BG_PRODUCT_TYPE_USDT_FUTURES)
                    .channel(BG_CHANNEL_TICKER)
                    .instId(config.getSymbol())
                    .build());
        }

        if (subscribeRequests.isEmpty()) return;

        taskExecutor.execute(() -> {
            try {
                bitgetCustomService.subscribeWsClientContractPublic(subscribeRequests, data -> {
                    if (data != null) {
                        BitgetWSMarketResp marketResp = JsonUtil.toBean(data, BitgetWSMarketResp.class);
                        if (marketResp.getData() != null && !marketResp.getData().isEmpty()) {
                            BitgetWSMarketResp.MarketInfo info = marketResp.getData().getFirst();
                            MARKET_PRICE_CACHE.put(info.getSymbol(), new BigDecimal(info.getLastPr()));
                        }
                    }
                });
            } catch (Exception e) {
                log.error("startWebSocketMarketDataMonitoring-error:", e);
            }
        });
    }

    /**
     * 发送区间价格信息邮件
     * 定时发送HTML格式的区间价格报告
     */
    public void sendRangePriceEmail() {
        if (RANGE_PRICE_CACHE.isEmpty()) return;

        try {
            StringBuilder content = new StringBuilder();
            content.append("<html><body>");
            content.append("<h2>📊 ").append(DateUtil.formatDateTime(new Date())).append("区间价格信息报告</h2>");
            content.append("<table border='1' cellpadding='8' cellspacing='0' style='border-collapse:collapse;'>");
            content.append("<thead><tr>")
                    .append("<th>币种</th>")
                    .append("<th>最高均价</th>")
                    .append("<th>最低均价</th>")
                    .append("<th>最高价</th>")
                    .append("<th>最高价时间</th>")
                    .append("<th>均价</th>")
                    .append("<th>最低价</th>")
                    .append("<th>最低价时间</th>")
                    .append("<th>区间数</th>")
                    .append("</tr></thead>");
            content.append("<tbody>");

            for (RangePrice rangePrice : RANGE_PRICE_CACHE.values()) {
                content.append("<tr>")
                        .append("<td>").append(rangePrice.getSymbol()).append("</td>")
                        .append("<td>").append(rangePrice.getHighAveragePrice()).append("</td>")
                        .append("<td>").append(rangePrice.getLowAveragePrice()).append("</td>")
                        .append("<td>").append(rangePrice.getHighPrice()).append("</td>")
                        .append("<td>").append(DateUtil.formatDateTime(new Date(rangePrice.getHighPriceTimestamp()))).append("</td>")
                        .append("<td>").append(rangePrice.getAveragePrice()).append("</td>")
                        .append("<td>").append(rangePrice.getLowPrice()).append("</td>")
                        .append("<td>").append(DateUtil.formatDateTime(new Date(rangePrice.getLowPriceTimestamp()))).append("</td>")
                        .append("<td>").append(rangePrice.getRangeCount()).append("</td>")
                        .append("</tr>");
            }
            content.append("</tbody></table>");
            content.append("<p style='color:gray;font-size:12px;'>此邮件为系统自动发送，请勿回复。</p>");
            content.append("</body></html>");

            // 发送HTML邮件
            mailService.sendHtmlMail(emailRecipient, DateUtil.now() + " 区间价格信息", content.toString());
        } catch (Exception e) {
            log.error("sendRangePriceEmail-error:", e);
        }
    }
}