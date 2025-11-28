package com.hy.modules.contract.service;

import cn.hutool.core.util.IdUtil;
import com.bitget.custom.entity.*;
import com.bitget.openapi.dto.request.ws.SubscribeReq;
import com.bitget.openapi.dto.response.ResponseResult;
import com.hy.common.enums.BitgetAccountType;
import com.hy.common.enums.BitgetEnum;
import com.hy.common.enums.SymbolEnum;
import com.hy.common.service.BitgetCustomService;
import com.hy.common.service.MailService;
import com.hy.common.utils.json.JsonUtil;
import com.hy.common.utils.num.AmountCalculator;
import com.hy.modules.contract.entity.DoubleMovingAverageData;
import com.hy.modules.contract.entity.DoubleMovingAveragePlaceOrder;
import com.hy.modules.contract.entity.DoubleMovingAverageStrategyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.stereotype.Service;
import org.ta4j.core.*;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.hy.common.constants.BitgetConstant.*;
import static com.hy.common.utils.num.AmountCalculator.calculateChangePercent;
import static com.hy.common.utils.num.BigDecimalUtils.*;
import static com.hy.common.utils.num.NumUtil.calculateExchangeMaxLeverage;

/****
 * 双均线策略服务类
 * 该类负责处理双均线策略的相关逻辑，包括计算指标、持仓信息管理等。
 */
@Slf4j
@Service
public class DoubleMovingAverageStrategyService {

    /**
     * Bitget自定义服务类
     **/
    private final BitgetCustomService bitgetCustomService;

    private final BitgetCustomService.BitgetSession bitgetSession;

    /**
     * 邮件通知服务
     */
    private final MailService mailService;

    /**
     * 线程池
     **/
    private final TaskExecutor taskExecutor;

    /**
     * 定时任务执行器
     */
    private final SimpleAsyncTaskScheduler taskScheduler;

    /**
     * 双均线平均价格
     **/
    private final static Map<String, DoubleMovingAverageData> DMAS_CACHE = new ConcurrentHashMap<>();

    /**
     * Bitget行情数据缓存
     **/
    private final static Map<String, BigDecimal> BTR_CACHE = new ConcurrentHashMap<>();

    /**
     * 订单队列 - 存储待执行的订单参数
     */
    private static final BlockingQueue<DoubleMovingAveragePlaceOrder> ORDER_QUEUE = new LinkedBlockingQueue<>(1000);


    /**
     * 订单消费者启动标志 - 确保只启动一次
     */
    private final AtomicBoolean ORDER_CONSUMER_STARTED = new AtomicBoolean(false);

    /**
     * K线数据的数量限制
     **/
    private final static Integer LIMIT = 1000;

    /**
     * 百分比计算常量
     **/
    private final static BigDecimal SPREAD_RATE = new BigDecimal("0.8");


    /**
     * 策略配置
     **/
    private final static Map<String, DoubleMovingAverageStrategyConfig> CONFIG_MAP = new ConcurrentHashMap<>() {
        {
            put(SymbolEnum.BTCUSDT.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.BTCUSDT.getCode(), BitgetEnum.H4.getCode(), 4, 1, 100, BigDecimal.valueOf(10.0), BigDecimal.valueOf(10.0)));
            put(SymbolEnum.ETHUSDT.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.ETHUSDT.getCode(), BitgetEnum.H4.getCode(), 2, 2, 100, BigDecimal.valueOf(10.0), BigDecimal.valueOf(20.0)));
            put(SymbolEnum.SOLUSDT.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.SOLUSDT.getCode(), BitgetEnum.H4.getCode(), 1, 3, 100, BigDecimal.valueOf(10.0), BigDecimal.valueOf(25.0)));
            put(SymbolEnum.ZECUSDT.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.ZECUSDT.getCode(), BitgetEnum.H4.getCode(), 3, 2, 75, BigDecimal.valueOf(10.0), BigDecimal.valueOf(30.0)));
            put(SymbolEnum.HYPEUSDT.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.HYPEUSDT.getCode(), BitgetEnum.H4.getCode(), 2, 3, 75, BigDecimal.valueOf(10.0), BigDecimal.valueOf(30.0)));
            put(SymbolEnum.DOGEUSDT.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.DOGEUSDT.getCode(), BitgetEnum.H4.getCode(), 0, 5, 75, BigDecimal.valueOf(10.0), BigDecimal.valueOf(30.0)));
            put(SymbolEnum.BNBUSDT.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.BNBUSDT.getCode(), BitgetEnum.H4.getCode(), 2, 2, 75, BigDecimal.valueOf(10.0), BigDecimal.valueOf(20.0)));
        }
    };

    /**
     * 是否允许开单（业务条件）
     * true = 没有仓位，可以开单
     * false = 有仓位，禁止开单
     */
    private final Map<String, AtomicBoolean> allowOpenByPosition = new ConcurrentHashMap<>();

    /**
     * 开单并发锁（true = 空闲，可以开单；false = 冷却中）
     */
    private final Map<String, AtomicBoolean> openLockMap = new ConcurrentHashMap<>();

    /**
     * 邮件接收地址
     */
    @Value("${spring.mail.username}")
    private String emailRecipient;


    public DoubleMovingAverageStrategyService(BitgetCustomService bitgetCustomService, MailService mailService, @Qualifier("applicationTaskExecutor") SimpleAsyncTaskExecutor taskExecutor, @Qualifier("taskScheduler") SimpleAsyncTaskScheduler taskScheduler) {
        this.bitgetCustomService = bitgetCustomService;
        this.bitgetSession = bitgetCustomService.use(BitgetAccountType.RANGE);
        this.mailService = mailService;
        this.taskExecutor = taskExecutor;
        this.taskScheduler = taskScheduler;
    }

    /**
     * 初始化方法双均线策略
     */
    public void init() {
        //初始化Bitget账户配置
        initializeBitgetAccount();
        //启动下单消费者
        startOrderConsumer();
        //WS行情数据监控
        marketDataWSMonitoring();
        log.info("双均线策略加载完成, 当前配置: {}", JsonUtil.toJson(CONFIG_MAP));
    }

    /**
     * 初始化Bitget账户配置
     * 设置杠杆、持仓模式和保证金模式等基础交易参数
     */
    public void initializeBitgetAccount() {
        try {
            for (DoubleMovingAverageStrategyConfig config : CONFIG_MAP.values()) {
                if (!config.getEnable()) continue;
                // 设置保证金模式为逐仓
                setMarginModeForSymbol(config);
                // 设置杠杆倍数 默认1倍
                //setLeverageForSymbol(config.getSymbol(), 1);
            }
            // 设置持仓模式为单向持仓
            setPositionMode();
        } catch (Exception e) {
            log.error("initializeBitgetAccount-error:", e);
        }
    }

    /**
     * 为指定币种设置保证金模式
     */
    private void setMarginModeForSymbol(DoubleMovingAverageStrategyConfig config) {
        try {
            ResponseResult<BitgetSetMarginModeResp> rs = bitgetSession.setMarginMode(config.getSymbol(), BG_PRODUCT_TYPE_USDT_FUTURES, DEFAULT_CURRENCY_USDT, BG_MARGIN_MODE_ISOLATED);
            log.info("setMarginModeForSymbol-设置保证金模式成功: symbol={}, result={}", config.getSymbol(), JsonUtil.toJson(rs));
        } catch (Exception e) {
            log.error("setMarginModeForSymbol-设置保证金模式失败: symbol={}", config.getSymbol(), e);
        }
    }

    /**
     * 为指定币种设置杠杆倍数
     */
    private void setLeverageForSymbol(String symbol, Integer leverage) {
        try {
            ResponseResult<BitgetSetLeverageResp> rs = bitgetSession.setLeverage(symbol, BG_PRODUCT_TYPE_USDT_FUTURES, DEFAULT_CURRENCY_USDT, leverage.toString(), null);
            log.info("setLeverageForSymbol-设置杠杆成功: symbol={}, leverage={}, result={}", symbol, leverage, JsonUtil.toJson(rs));
        } catch (Exception e) {
            log.error("setLeverageForSymbol-设置杠杆失败: symbol={}, leverage={}", symbol, leverage, e);
        }
    }

    /**
     * 设置持仓模式为单向持仓
     */
    private void setPositionMode() {
        try {
            ResponseResult<BitgetSetPositionModeResp> rs = bitgetSession.setPositionMode(BG_PRODUCT_TYPE_USDT_FUTURES, BG_POS_MODE_ONE_WAY_MODE);
            log.info("setPositionMode-设置持仓模式成功: result={}", JsonUtil.toJson(rs));
        } catch (Exception e) {
            log.error("setPositionMode-设置持仓模式失败:", e);
        }
    }


    /**
     * 双均线数据监控
     **/
    public void doubleMovingAverageDataMonitoring() {
        for (DoubleMovingAverageStrategyConfig config : CONFIG_MAP.values()) {
            taskExecutor.execute(() -> {
                try {
                    ResponseResult<List<BitgetMixMarketCandlesResp>> rs = bitgetSession.getMinMarketCandles(config.getSymbol(), BG_PRODUCT_TYPE_USDT_FUTURES, config.getTimeFrame(), LIMIT);
                    if (rs.getData() == null || rs.getData().isEmpty()) return;
                    if (rs.getData().size() < 500) return;
                    BarSeries barSeries = buildSeriesFromBitgetCandles(rs.getData(), Objects.requireNonNull(BitgetEnum.getByCode(config.getTimeFrame())).getDuration());
                    DoubleMovingAverageData data = calculateIndicators(barSeries);
                    DMAS_CACHE.put(config.getSymbol(), data);
                } catch (Exception e) {
                    log.error("doubleMovingAverageDataMonitoring-error:{}", config.getSymbol(), e);
                }
            });
        }
    }

    /**
     * 行情数据监控
     */
    public void marketDataMonitoring() {
        for (DoubleMovingAverageStrategyConfig config : CONFIG_MAP.values()) {
            taskExecutor.execute(() -> {
                try {
                    ResponseResult<List<BitgetMixMarketTickerResp>> rs = bitgetSession.getMixMarketTicker(config.getSymbol(), BG_PRODUCT_TYPE_USDT_FUTURES);
                    if (rs.getData() == null || rs.getData().isEmpty()) return;
                    BTR_CACHE.put(config.getSymbol(), new BigDecimal(rs.getData().getFirst().getLastPr()));
                } catch (Exception e) {
                    log.error("marketDataMonitoring-error:{}", config.getSymbol(), e);
                }
            });
        }
    }

    /**
     * 双均线策略信号下单监控
     */
    public void signalOrderMonitoring() {
        try {
            if (DMAS_CACHE.isEmpty() || BTR_CACHE.isEmpty()) return;
            DMAS_CACHE.forEach((symbol, data) -> {
                DoubleMovingAverageStrategyConfig conf = CONFIG_MAP.get(symbol);
                if (!conf.getEnable() || !BTR_CACHE.containsKey(conf.getSymbol())) return;

                // 1. 仓位状态检查（必须允许开单），统一获取/创建状态对象（默认 false，不允许）
                AtomicBoolean allowOpen = allowOpenByPosition.computeIfAbsent(symbol, k -> new AtomicBoolean(false));
                if (!allowOpen.get()) return;

                if (!isStrictMATrendConfirmed(data)) return;
                BigDecimal latestPrice = BTR_CACHE.get(conf.getSymbol());
                DoubleMovingAveragePlaceOrder order = null;
                //多空判断
                if (gt(latestPrice, data.getMa144()) && gt(latestPrice, data.getEma144()) && (gt(data.getMa21(), data.getMa144()) || gt(data.getEma21(), data.getEma144()))) {
                    BigDecimal upPriceRange = data.getMaxValue();
                    BigDecimal downPriceRange = data.getMinValue();
                    //中间价区间
                    BigDecimal medianPrice = downPriceRange.add(upPriceRange.subtract(downPriceRange).multiply(BigDecimal.valueOf(0.5)));
                    BigDecimal medianPriceDecrease = AmountCalculator.decrease(medianPrice, BigDecimal.valueOf(0.3), conf.getPricePlace());
                    //判断 latestPrice 是否在 medianPrice ,medianPriceDecrease 之间
                    if (gte(latestPrice, medianPriceDecrease) && lt(latestPrice, medianPrice)) {
                        //符合多头开多条件，预处理下单信息
                        order = createPlaceOrder(conf, BG_SIDE_BUY, latestPrice, downPriceRange);
                    }
                } else if (lt(latestPrice, data.getMa144()) && lt(latestPrice, data.getEma144()) && (lt(data.getMa21(), data.getMa144()) || lt(data.getEma21(), data.getEma144()))) {
                    BigDecimal downPriceRange = data.getMinValue();
                    BigDecimal upPriceRange = data.getMaxValue();
                    //中间价区间
                    BigDecimal medianPrice = downPriceRange.add(upPriceRange.subtract(downPriceRange).multiply(BigDecimal.valueOf(0.5)));
                    BigDecimal medianPriceIncrease = AmountCalculator.increase(medianPrice, BigDecimal.valueOf(0.3), conf.getPricePlace());
                    //判断 latestPrice 是否在 medianPriceIncrease ,medianPrice 之间
                    if (gt(latestPrice, medianPrice) && lte(latestPrice, medianPriceIncrease)) {
                        //符合空头开空条件，预处理下单信息
                        order = createPlaceOrder(conf, BG_SIDE_SELL, latestPrice, upPriceRange);
                    }
                }
                if (order != null && tryAcquireOpenLock(symbol)) {
                    // 获取用于写入的 allowOpen 对象（如果之前不存在，则认为允许开单）
                    AtomicBoolean allowOpenForSet = allowOpenByPosition.computeIfAbsent(symbol, k -> new AtomicBoolean(true));
                    if (ORDER_QUEUE.offer(order)) {
                        // 成功入队后再禁止该 symbol 继续开单
                        allowOpenForSet.set(false);
                        log.info("signalOrderMonitoring:检测到双均线交易信号，已放入下单队列，order:{}", JsonUtil.toJson(order));
                    } else {
                        // 入队失败，立即释放开单锁，允许快速重试
                        AtomicBoolean lock = openLockMap.get(symbol);
                        if (lock != null) {
                            lock.set(true);
                        }
                        log.warn("signalOrderMonitoring: 下单队列已满，放入失败，symbol={}", symbol);
                    }
                }
            });
        } catch (Exception e) {
            log.error("signalOrderMonitoring-error", e);
        }
    }


    /**
     * 获取开仓锁
     **/
    private boolean tryAcquireOpenLock(String symbol) {
        openLockMap.putIfAbsent(symbol, new AtomicBoolean(true));
        AtomicBoolean lock = openLockMap.get(symbol);
        if (lock.compareAndSet(true, false)) {
            // 10秒后自动释放锁
            taskScheduler.schedule(() -> lock.set(true), Instant.now().plusSeconds(10));
            return true;
        }
        return false;
    }

    /**
     * 检测是否形成严格的多重均线趋势排列
     * 满足MA21/55/144和EMA21/55/144的多重验证条件
     */
    public boolean isStrictMATrendConfirmed(DoubleMovingAverageData data) {
        return isLongTrend(data) || isShortTrend(data);
    }

    /**
     * 多头趋势检测
     */
    private boolean isLongTrend(DoubleMovingAverageData data) {
        // MA条件: ma21 > ma55 且 ma21 > ma144
        boolean maCondition = gt(data.getMa21(), data.getMa55()) && gt(data.getMa21(), data.getMa144());
        // EMA条件: ema21 > ema55 且 ema21 > ema144
        boolean emaCondition = gt(data.getEma21(), data.getEma55()) && gt(data.getEma21(), data.getEma144());
        // 交叉条件: ma21 > ema55 且 ma21 > ema144
        boolean maCrossCondition = gt(data.getMa21(), data.getEma55()) && gt(data.getMa21(), data.getEma144());
        // EMA交叉条件: ema21 > ma55 且 ema21 > ma144
        boolean emaCrossCondition = gt(data.getEma21(), data.getMa55()) && gt(data.getEma21(), data.getMa144());
        // 中期均线条件: ma55或ema55大于ma144或ema144
        boolean midTermCondition = (gt(data.getMa55(), data.getMa144()) && gt(data.getMa55(), data.getEma144())) || (gt(data.getEma55(), data.getMa144()) && gt(data.getEma55(), data.getEma144()));
        return maCondition && emaCondition && maCrossCondition && emaCrossCondition && midTermCondition;
    }

    /**
     * 空头趋势检测
     */
    private boolean isShortTrend(DoubleMovingAverageData data) {
        // MA条件: ma21 < ma55 且 ma21 < ma144
        boolean maCondition = lt(data.getMa21(), data.getMa55()) && lt(data.getMa21(), data.getMa144());
        // EMA条件: ema21 < ema55 且 ema21 < ema144
        boolean emaCondition = lt(data.getEma21(), data.getEma55()) && lt(data.getEma21(), data.getEma144());
        // 交叉条件: ma21 < ema55 且 ma21 < ema144
        boolean maCrossCondition = lt(data.getMa21(), data.getEma55()) && lt(data.getMa21(), data.getEma144());
        // EMA交叉条件: ema21 < ma55 且 ema21 < ma144
        boolean emaCrossCondition = lt(data.getEma21(), data.getMa55()) && lt(data.getEma21(), data.getMa144());
        // 中期均线条件: ma55或ema55小于ma144或ema144
        boolean midTermCondition = (lt(data.getMa55(), data.getMa144()) && lt(data.getMa55(), data.getEma144())) || (lt(data.getEma55(), data.getMa144()) && lt(data.getEma55(), data.getEma144()));
        return maCondition && emaCondition && maCrossCondition && emaCrossCondition && midTermCondition;
    }

    /**
     * 创建双均线下单信息
     */
    public DoubleMovingAveragePlaceOrder createPlaceOrder(DoubleMovingAverageStrategyConfig conf, String side, BigDecimal latestPrice, BigDecimal presetStopLossPrice) {
        DoubleMovingAveragePlaceOrder order = new DoubleMovingAveragePlaceOrder();
        order.setClientOid(IdUtil.getSnowflakeNextIdStr());
        order.setSymbol(conf.getSymbol());
        order.setSide(side);
        order.setPresetStopLossPrice(presetStopLossPrice.setScale(conf.getPricePlace(), RoundingMode.HALF_UP).toPlainString());
        order.setOrderType(BG_ORDER_TYPE_MARKET);
        order.setMarginMode(BG_MARGIN_MODE_ISOLATED);
        //计算涨跌幅百分比
        BigDecimal changePercent = calculateChangePercent(presetStopLossPrice, latestPrice).abs();
        //计算最大可开杠杆
        int actualLeverage = calculateExchangeMaxLeverage(changePercent, conf.getMaxLeverage());
        // 计算实际开仓数量
        BigDecimal realityOpenAmount = conf.getOpenAmount().multiply(BigDecimal.valueOf(actualLeverage));
        BigDecimal size = realityOpenAmount.divide(latestPrice, conf.getVolumePlace(), RoundingMode.HALF_UP);
        order.setSize(size.toPlainString());
        order.setLeverage(actualLeverage);
        return order;
    }

    /**
     * 获取所有仓位
     **/
    public Map<String, BitgetAllPositionResp> getAllPosition() throws IOException {
        ResponseResult<List<BitgetAllPositionResp>> positionResp = bitgetSession.getAllPosition();
        List<BitgetAllPositionResp> positions = Optional.ofNullable(positionResp.getData()).orElse(Collections.emptyList());
        return positions.stream().collect(Collectors.toMap(BitgetAllPositionResp::getSymbol, p -> p, (existing, replacement) -> existing));
    }

    /**
     * 验证账户余额
     */
    private boolean validateAccountBalance(DoubleMovingAveragePlaceOrder placeOrder) {
        Map<String, BitgetAccountsResp> accountMap = getAccountInfo();
        BitgetAccountsResp accountsResp = accountMap.get(DEFAULT_CURRENCY_USDT);
        if (accountsResp == null) {
            log.warn("validateAccountBalance: 未获取到USDT账户信息，无法执行下单! 订单: {}", JsonUtil.toJson(placeOrder));
            return false;
        }
        DoubleMovingAverageStrategyConfig config = CONFIG_MAP.get(placeOrder.getSymbol());
        BigDecimal available = new BigDecimal(accountsResp.getAvailable());
        BigDecimal isolatedMaxAvailable = new BigDecimal(accountsResp.getIsolatedMaxAvailable());
        BigDecimal maxInvestAmount = config.getOpenAmount();
        placeOrder.setAccountBalance(available);
        if (lt(available, maxInvestAmount) || lt(isolatedMaxAvailable, maxInvestAmount)) {
            log.warn("validateAccountBalance: USDT账户可用余额不足，无法执行下单操作! 订单: {} 可用余额: {}, 逐仓最大可用来开仓余额: {}", JsonUtil.toJson(placeOrder), available, isolatedMaxAvailable);
            return false;
        }
        return true;
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
                        DoubleMovingAveragePlaceOrder orderParam = ORDER_QUEUE.take(); // 阻塞直到有数据
                        // 校验当前是否已有仓位
                        if (getAllPosition().containsKey(orderParam.getSymbol())) continue;
                        // 校验账户余额
                        if (!validateAccountBalance(orderParam)) continue;
                        //设置杠杆
                        setLeverageForSymbol(orderParam.getSymbol(), orderParam.getLeverage());
                        // 执行下单
                        ResponseResult<BitgetPlaceOrderResp> orderResult = executeOrder(orderParam);
                        log.info("startOrderConsumer: 下单完成，订单信息: {}, 返回结果: {}", JsonUtil.toJson(orderParam), JsonUtil.toJson(orderResult));
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
     * 执行下单操作
     */
    private ResponseResult<BitgetPlaceOrderResp> executeOrder(DoubleMovingAveragePlaceOrder orderParam) throws Exception {
        return bitgetSession.placeOrder(
                orderParam.getClientOid(),
                orderParam.getSymbol(),
                orderParam.getSize(),
                orderParam.getSide(),
                null,
                orderParam.getOrderType(),
                orderParam.getMarginMode(),
                orderParam.getPresetStopLossPrice());
    }


    /****
     * 从 Bitget K 线数据构建 BarSeries
     * @param candles        Bitget K 线数据列表
     * @param candleDuration K 线周期，如 Duration.ofMinutes(1)
     * @return 返回构建好的 BarSeries
     */
    public static BarSeries buildSeriesFromBitgetCandles(List<BitgetMixMarketCandlesResp> candles, Duration candleDuration) {
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance()).build();
        for (BitgetMixMarketCandlesResp candle : candles) {
            Bar bar = new BaseBar(
                    candleDuration,
                    Instant.ofEpochMilli(candle.getTimestamp()),
                    null,
                    series.numFactory().numOf(candle.getOpenPrice()),
                    series.numFactory().numOf(candle.getHighPrice()),
                    series.numFactory().numOf(candle.getLowPrice()),
                    series.numFactory().numOf(candle.getClosePrice()),
                    series.numFactory().numOf(candle.getBaseVolume()),   // volume
                    series.numFactory().numOf(candle.getQuoteVolume()),  // amount (可以用 quoteVolume)
                    0L
            );
            series.addBar(bar);
        }
        return series;
    }

    /**
     * 计算最新双均线指标
     * MA21 , EMA21 ,MA55 , EMA55, MA144 , EMA144
     **/
    public static DoubleMovingAverageData calculateIndicators(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        Indicator<Num> ma21 = new SMAIndicator(closePrice, 21);
        Indicator<Num> ema21 = new EMAIndicator(closePrice, 21);
        Indicator<Num> ma55 = new SMAIndicator(closePrice, 55);
        Indicator<Num> ema55 = new EMAIndicator(closePrice, 55);
        Indicator<Num> ma144 = new SMAIndicator(closePrice, 144);
        Indicator<Num> ema144 = new EMAIndicator(closePrice, 144);
        int endIndex = series.getEndIndex();
        return new DoubleMovingAverageData(
                ma21.getValue(endIndex).bigDecimalValue(),
                ma55.getValue(endIndex).bigDecimalValue(),
                ma144.getValue(endIndex).bigDecimalValue(),
                ema21.getValue(endIndex).bigDecimalValue(),
                ema55.getValue(endIndex).bigDecimalValue(),
                ema144.getValue(endIndex).bigDecimalValue());
    }


    /**
     * 获取账户信息
     **/
    public Map<String, BitgetAccountsResp> getAccountInfo() {
        try {
            ResponseResult<List<BitgetAccountsResp>> accountsResp = bitgetSession.getAccounts();
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
     * WS行情数据监控
     */
    public void marketDataWSMonitoring() {
        List<SubscribeReq> list = new ArrayList<>();
        for (DoubleMovingAverageStrategyConfig config : CONFIG_MAP.values()) {
            list.add(SubscribeReq.builder().instType(BG_PRODUCT_TYPE_USDT_FUTURES).channel(BG_CHANNEL_TICKER).instId(config.getSymbol()).build());
        }
        if (list.isEmpty()) return;
        taskExecutor.execute(() -> {
            try {
                bitgetCustomService.subscribeWsClientContractPublic(list, data -> {
                    if (data != null) {
                        BitgetWSMarketResp marketResp = JsonUtil.toBean(data, BitgetWSMarketResp.class);
                        if (marketResp.getData() != null && !marketResp.getData().isEmpty()) {
                            BitgetWSMarketResp.MarketInfo info = marketResp.getData().getFirst();
                            BTR_CACHE.put(info.getSymbol(), new BigDecimal(info.getLastPr()));
                        }
                    }
                });
            } catch (Exception e) {
                log.error("marketDataWSMonitoring-error:", e);
            }
        });
    }

    /**
     * 仓位管理
     */
    public void managePositions() {
        try {
            // 获取当前所有持仓
            Map<String, BitgetAllPositionResp> positionMap = getAllPosition();

            // 根据仓位更新是否允许开单
            CONFIG_MAP.keySet().forEach(symbol -> {
                boolean hasPosition = positionMap.containsKey(symbol);
                allowOpenByPosition.computeIfAbsent(symbol, k -> new AtomicBoolean(false)).set(!hasPosition);
            });

            // 必须有仓位才能执行后续操作
            if (positionMap.isEmpty()) return;

            // 获取当前计划止盈止损委托
            Map<String, List<BitgetOrdersPlanPendingResp.EntrustedOrder>> entrustedOrdersMap = getOrdersPlanPending();
            //log.info("managePositions: 当前持仓: {}, 当前计划止盈止损委托: {}", JsonUtil.toJson(positionMap), JsonUtil.toJson(entrustedOrdersMap));
            // 更新止盈止损计划
            updateTpslPlans(positionMap, entrustedOrdersMap);
        } catch (Exception e) {
            log.error("managePositions-error", e);
        }
    }

    /**
     * 更新止盈止损计划
     **/
    public void updateTpslPlans(Map<String, BitgetAllPositionResp> positionMap, Map<String, List<BitgetOrdersPlanPendingResp.EntrustedOrder>> entrustedOrdersMap) {
        if (positionMap == null || positionMap.isEmpty()) return;
        if (entrustedOrdersMap == null || entrustedOrdersMap.isEmpty()) return;

        positionMap.forEach((symbol, position) -> {
            try {
                DoubleMovingAverageStrategyConfig config = CONFIG_MAP.get(symbol);
                if (config == null) return;

                BigDecimal latestPrice = BTR_CACHE.get(config.getSymbol());
                DoubleMovingAverageData data = DMAS_CACHE.get(config.getSymbol());
                if (latestPrice == null || data == null) return;
                //仓位盈亏平衡价
                BigDecimal breakEvenPrice = new BigDecimal(position.getBreakEvenPrice()).setScale(config.getPricePlace(), RoundingMode.HALF_UP);

                // 计算动态止赢价（基于 ma144 与 latestPrice）
                BigDecimal stopProfitPrice = BigDecimal.ZERO;
                BigDecimal ma144 = data.getMa144();
                //计算涨跌幅百分比
                BigDecimal change = calculateChangePercent(ma144, latestPrice).abs();
                boolean isStopProfit = gt(change, config.getDeviationFromMA144());
                BigDecimal coefficient = SPREAD_RATE;
                if (isStopProfit) {
                    coefficient = coefficient.add(change.subtract(config.getDeviationFromMA144()).multiply(BigDecimal.valueOf(0.01))).min(BigDecimal.ONE);
                }
                if (gt(latestPrice, ma144) && isStopProfit) {
                    BigDecimal spread = latestPrice.subtract(ma144).multiply(coefficient);
                    stopProfitPrice = ma144.add(spread).setScale(config.getPricePlace(), RoundingMode.HALF_UP);
                } else if (lt(latestPrice, ma144) && isStopProfit) {
                    BigDecimal spread = ma144.subtract(latestPrice).multiply(coefficient);
                    stopProfitPrice = ma144.subtract(spread).setScale(config.getPricePlace(), RoundingMode.HALF_UP);
                }

                List<BitgetOrdersPlanPendingResp.EntrustedOrder> entrustedOrders = entrustedOrdersMap.get(symbol);
                if (entrustedOrders == null || entrustedOrders.isEmpty()) return;

                for (BitgetOrdersPlanPendingResp.EntrustedOrder order : entrustedOrders) {
                    try {
                        if (!BG_PLAN_TYPE_LOSS_PLAN.equals(order.getPlanType())) continue;
                        BigDecimal triggerPrice = new BigDecimal(Optional.ofNullable(order.getTriggerPrice()).orElse("0"));
                        String side = order.getSide();
                        //做多 sell 卖
                        if (BG_SIDE_SELL.equals(side)) {
                            BigDecimal newTriggerPrice = BigDecimal.ZERO;
                            if (gt(data.getMin144Value(), breakEvenPrice)) {
                                newTriggerPrice = data.getMin144Value();
                            }
                            if (gt(stopProfitPrice, BigDecimal.ZERO) && lt(triggerPrice, stopProfitPrice) && gt(stopProfitPrice, newTriggerPrice)) {
                                newTriggerPrice = stopProfitPrice;
                            }
                            if (ne(triggerPrice, newTriggerPrice) && gt(newTriggerPrice, triggerPrice)) {
                                modifyStopLossOrder(order, newTriggerPrice);
                            }
                        }
                        //做空 buy 买
                        else if (BG_SIDE_BUY.equals(side)) {
                            BigDecimal newTriggerPrice = BigDecimal.ZERO;
                            if (lt(data.getMax144Value(), breakEvenPrice)) {
                                newTriggerPrice = data.getMax144Value();
                            }
                            if (gt(stopProfitPrice, BigDecimal.ZERO) && gt(triggerPrice, stopProfitPrice) && lt(stopProfitPrice, newTriggerPrice)) {
                                newTriggerPrice = stopProfitPrice;
                            }
                            if (ne(triggerPrice, newTriggerPrice) && lt(newTriggerPrice, triggerPrice) && gt(newTriggerPrice, BigDecimal.ZERO)) {
                                modifyStopLossOrder(order, newTriggerPrice);
                            }
                        }
                    } catch (Exception inner) {
                        log.error("updateTpslPlans: 单个委托处理失败 symbol={}, orderId={}, error={}", symbol, order.getOrderId(), inner.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("updateTpslPlans-error", e);
            }
        });
    }


    /**
     * 获取当前计划委托
     **/
    public Map<String, List<BitgetOrdersPlanPendingResp.EntrustedOrder>> getOrdersPlanPending() throws IOException {
        ResponseResult<BitgetOrdersPlanPendingResp> planResp = bitgetSession.getOrdersPlanPending(BG_PLAN_TYPE_PROFIT_LOSS, BG_PRODUCT_TYPE_USDT_FUTURES);
        BitgetOrdersPlanPendingResp data = planResp.getData();
        if (data == null || data.getEntrustedList() == null) return Map.of();
        return data.getEntrustedList().stream().collect(Collectors.groupingBy(BitgetOrdersPlanPendingResp.EntrustedOrder::getSymbol));
    }

    /**
     * 修改止盈止损计划
     */
    private void modifyStopLossOrder(BitgetOrdersPlanPendingResp.EntrustedOrder order, BigDecimal newTriggerPrice) {
        try {
            BitgetModifyTpslOrderParam param = new BitgetModifyTpslOrderParam();
            param.setOrderId(order.getOrderId());
            param.setMarginCoin(order.getMarginCoin());
            param.setProductType(BG_PRODUCT_TYPE_USDT_FUTURES);
            param.setSymbol(order.getSymbol());
            param.setTriggerPrice(newTriggerPrice.toPlainString());
            param.setTriggerType(BG_TRIGGER_TYPE_FILL_PRICE);
            param.setSize("");
            ResponseResult<BitgetPlaceTpslOrderResp> result = bitgetSession.modifyTpslOrder(param);
            if (!BG_RESPONSE_CODE_SUCCESS.equals(result.getCode())) {
                log.error("modifyStopLossOrder: 修改止盈止损计划失败, param: {}, result: {}", JsonUtil.toJson(param), JsonUtil.toJson(result));
            }
        } catch (Exception e) {
            log.error("modifyStopLossOrder-error: 更新止盈止损计划失败, order: {}, newTriggerPrice: {}, error: {}", JsonUtil.toJson(order), newTriggerPrice, e.getMessage());
        }
    }

    /**
     * 发送邮件通知
     **/
    public void sendEmail(String subject, String content) {
        mailService.sendSimpleMail(emailRecipient, subject, content);
    }
}
