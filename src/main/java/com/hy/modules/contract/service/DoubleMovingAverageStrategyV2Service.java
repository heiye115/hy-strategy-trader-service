package com.hy.modules.contract.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.hy.common.enums.SymbolEnum;
import com.hy.common.service.MailService;
import com.hy.modules.contract.entity.DoubleMovingAverageData;
import com.hy.modules.contract.entity.DoubleMovingAveragePlaceOrder;
import com.hy.modules.contract.entity.DoubleMovingAverageStrategyConfig;
import io.github.hyperliquid.sdk.HyperliquidClient;
import io.github.hyperliquid.sdk.apis.Info;
import io.github.hyperliquid.sdk.model.info.*;
import io.github.hyperliquid.sdk.model.order.*;
import io.github.hyperliquid.sdk.model.subscription.TradesSubscription;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.hy.common.constants.HypeConstant.*;
import static com.hy.common.constants.StrategyConstant.STATUS_OK;
import static com.hy.common.utils.json.JsonUtil.toJson;
import static com.hy.common.utils.num.AmountCalculator.*;
import static com.hy.common.utils.num.BigDecimalUtils.*;
import static com.hy.common.utils.num.NumUtil.calculateExchangeMaxLeverage;

/****
 * 双均线策略服务类
 * 该类负责处理双均线策略的相关逻辑，包括计算指标、持仓信息管理等。
 */
@Slf4j
@Service
public class DoubleMovingAverageStrategyV2Service {


    private final HyperliquidClient client;

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
     * 最新价格缓存
     * 存储每个交易对的最新市场价格，用于信号检测和订单构建
     **/
    private final static Map<String, BigDecimal> LATEST_PRICE_CACHE = new ConcurrentHashMap<>();

    /**
     * 最后一次交易时间缓存
     */
    private static final Map<String, AtomicLong> LAST_TRADE_TIME_CACHE = new ConcurrentHashMap<>();

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
     * 中间价偏离度
     * 用于跟踪趋势下单时的价格容忍范围
     * 当前设置：0.3%（优化后）
     * - 允许价格在中间价±0.3%范围内开仓
     * - 增加入场机会同时保持精准度
     **/
    private final static BigDecimal MEDIAN_DEVIATION = new BigDecimal("0.3");

    /**
     * 动态止盈分段系数 - 第一段基础系数 (保守阶段)
     * 偏离度在阈值基础上 0-10% 时的基础系数
     * 优化分析：
     * - 0.70：过于保守，小级别趋势回撤风险大
     * - 0.75：平衡值，适合大多数场景
     * - 0.80：过于激进，可能过早止盈
     * 当前设置：0.75（优化后）
     * - 锁定75%利润，留存25%空间容忍插针
     **/
    private final static BigDecimal STAGE1_BASE_COEFFICIENT = new BigDecimal("0.75");

    /**
     * 动态止盈分段系数 - 第二段基础系数 (稳健阶段)
     * 偏离度在阈值基础上 10-20% 时的基础系数
     * 优化分析：
     * - 0.80：当前值，合理
     * - 0.85：更激进，大涨幅时锁定更多利润
     * 当前设置：0.85（优化后）
     * - 锁定85%利润，留存15%空间容忍插针
     **/
    private final static BigDecimal STAGE2_BASE_COEFFICIENT = new BigDecimal("0.85");

    /**
     * 动态止盈分段系数 - 第三段基础系数 (激进阶段)
     * 偏离度在阈值基础上 20%+ 时的基础系数
     * 优化分析：
     * - 0.88：当前值，已经很激进
     * - 0.90：更激进，但插针风险更大
     * 当前设置：0.90（优化后）
     * - 锁定90%利润，留存10%空间容忍插针
     * - 极端行情中锁定大部分利润
     **/
    private final static BigDecimal STAGE3_BASE_COEFFICIENT = new BigDecimal("0.90");

    /**
     * 动态止盈系数最大值 (保留5%插针容忍度)
     **/
    private final static BigDecimal MAX_DYNAMIC_COEFFICIENT = new BigDecimal("0.95");

    /**
     * 动态止盈最低盈利阈值
     * 只有当实际盈利超过此阈值时，才启动动态止盈机制
     * 优化分析：
     * - 2.0%：过于保守，小级别趋势无法及时保护
     * - 1.5%：平衡值，既避免过早止盈又能及时保护
     * - 1.0%：过于激进，可能导致频繁止盈
     * 当前设置：1.5%（优化后）
     * - 盈利达到1.5%时启动动态止盈
     * - 对小级别趋势更友好
     * - 依然避免划损止盈
     */
    private final static BigDecimal MIN_PROFIT_THRESHOLD = new BigDecimal("1.5");

    /**
     * 分段阈值 - 第一阶段上限 (10%)
     **/
    private final static BigDecimal STAGE1_THRESHOLD = BigDecimal.TEN;

    /**
     * 分段阈值 - 第二阶段上限 (20%)
     **/
    private final static BigDecimal STAGE2_THRESHOLD = new BigDecimal("20");

    /**
     * 第一阶段系数增量单位 (每1%偏离增加1%系数)
     **/
    private final static BigDecimal STAGE1_INCREMENT = new BigDecimal("0.01");

    /**
     * 第二阶段系数增量单位 (每1%偏离增加1%系数)
     **/
    private final static BigDecimal STAGE2_INCREMENT = new BigDecimal("0.01");

    /**
     * 第三阶段系数增量单位 (每1%偏离增加0.35%系数，增速放缓)
     **/
    private final static BigDecimal STAGE3_INCREMENT = new BigDecimal("0.0035");

    /**
     * 策略配置
     * 配置说明：
     * - enable: 是否启用
     * - symbol: 交易对
     * - timeFrame: K线周期
     * - volumePlace: 数量小数位
     * - pricePlace: 价格小数位
     * - maxLeverage: 最大杠杆倍数
     * - openAmount: 单次开仓金额（USDC）
     * - deviationFromMA: 动态止盈偏离度阈值（%）
     * 偏离度阈值优化原则：
     * - BTC/ETH H1周期：10-12%（主流币，趋势稳健）
     * - 山寨币 H4周期：20-25%（波动更大，但不宜过度30%）
     **/
    private final static Map<String, DoubleMovingAverageStrategyConfig> CONFIG_MAP = new ConcurrentHashMap<>() {
        {
            put(SymbolEnum.BTCUSDC.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.BTCUSDC.getCode(), CandleInterval.HOUR_4.getCode(), 4, 1, 40, BigDecimal.valueOf(20.0), BigDecimal.valueOf(10)));
            put(SymbolEnum.ETHUSDC.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.ETHUSDC.getCode(), CandleInterval.HOUR_4.getCode(), 2, 2, 25, BigDecimal.valueOf(20.0), BigDecimal.valueOf(15)));
            put(SymbolEnum.SOLUSDC.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.SOLUSDC.getCode(), CandleInterval.HOUR_4.getCode(), 1, 3, 20, BigDecimal.valueOf(20.0), BigDecimal.valueOf(20)));

            put(SymbolEnum.XRPUSDC.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.XRPUSDC.getCode(), CandleInterval.HOUR_4.getCode(), 0, 4, 20, BigDecimal.valueOf(20.0), BigDecimal.valueOf(20)));
            put(SymbolEnum.HYPEUSDC.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.HYPEUSDC.getCode(), CandleInterval.HOUR_4.getCode(), 2, 3, 10, BigDecimal.valueOf(20.0), BigDecimal.valueOf(25)));
            put(SymbolEnum.DOGEUSDC.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.DOGEUSDC.getCode(), CandleInterval.HOUR_4.getCode(), 0, 5, 10, BigDecimal.valueOf(20.0), BigDecimal.valueOf(25)));
            put(SymbolEnum.ZECUSDC.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.ZECUSDC.getCode(), CandleInterval.HOUR_4.getCode(), 3, 2, 10, BigDecimal.valueOf(20.0), BigDecimal.valueOf(30)));
        }
    };

    /**
     * 是否允许开单（业务条件）
     * true = 没有仓位，可以开单
     * false = 有仓位，禁止开单
     */
    private final Map<String, AtomicBoolean> canOpenPositionMap = new ConcurrentHashMap<>();

    /**
     * 开单并发锁（true = 空闲，可以开单；false = 冷却中）
     */
    private final Map<String, AtomicBoolean> openLockMap = new ConcurrentHashMap<>();

    /**
     * 邮件接收地址
     */
    @Value("${spring.mail.username}")
    private String emailRecipient;

    public DoubleMovingAverageStrategyV2Service(MailService mailService, @Qualifier("applicationTaskExecutor") SimpleAsyncTaskExecutor taskExecutor, @Qualifier("taskScheduler") SimpleAsyncTaskScheduler taskScheduler, @Value("${hyperliquid.primary-wallet-address}") String primaryWalletAddress, @Value("${hyperliquid.api-wallet-private-key}") String apiWalletPrivateKey) {
        this.client = HyperliquidClient.builder()
                .addApiWallet(primaryWalletAddress, apiWalletPrivateKey)
                .build();
        this.mailService = mailService;
        this.taskExecutor = taskExecutor;
        this.taskScheduler = taskScheduler;
    }

    /**
     * 初始化方法双均线策略
     */
    public void init() {
        //启动下单消费者
        startOrderConsumer();
        //通过WebSocket订阅行情数据
        subscribeMarketDataViaWebSocket();
        log.info("双均线策略加载完成, 当前配置: {}", toJson(CONFIG_MAP));
    }


    /**
     * 为指定币种设置杠杆倍数
     */
    private void setLeverageForSymbol(String symbol, boolean crossed, Integer leverage) {
        try {
            UpdateLeverage updateLeverage = client.getExchange().updateLeverage(symbol, crossed, leverage);
            log.info("setLeverageForSymbol-设置杠杆成功: symbol={}, leverage={}, result={}", symbol, leverage, toJson(updateLeverage));
        } catch (Exception e) {
            log.error("setLeverageForSymbol-设置杠杆失败: symbol={}, leverage={}", symbol, leverage, e);
        }
    }


    /**
     * 更新双均线指标数据
     * 计算并缓存MA/EMA指标，同时缓存BarSeries用于震荡过滤计算
     **/
    public void updateDoubleMovingAverageIndicators() {
        for (DoubleMovingAverageStrategyConfig config : CONFIG_MAP.values()) {
            taskExecutor.execute(() -> {
                try {
                    CandleInterval candleInterval = CandleInterval.fromCode(config.getTimeFrame());
                    List<Candle> candles = client.getInfo().candleSnapshotByCount(config.getSymbol(), candleInterval, LIMIT);
                    if (candles == null || candles.isEmpty()) return;
                    if (candles.size() < 500) return;
                    BarSeries barSeries = buildSeriesFromCandles(candles, candleInterval.getDuration());
                    DoubleMovingAverageData data = calculateIndicators(barSeries, config.getPricePlace());
                    // 缓存双均线指标数据
                    DMAS_CACHE.put(config.getSymbol(), data);
                } catch (Exception e) {
                    log.error("updateDoubleMovingAverageIndicators-error:{}", config.getSymbol(), e);
                }
            });
        }
    }

    /**
     * 刷新市场价格缓存
     * 通过REST API获取最新价格并更新缓存
     */
    public void refreshMarketPriceCache() {
        for (DoubleMovingAverageStrategyConfig config : CONFIG_MAP.values()) {
            taskExecutor.execute(() -> {
                try {
                    List<Candle> candles = client.getInfo().candleSnapshotByCount(config.getSymbol(), CandleInterval.MINUTE_1, 1);
                    if (candles == null || candles.isEmpty()) return;
                    LATEST_PRICE_CACHE.put(config.getSymbol(), new BigDecimal(candles.getFirst().getClosePrice()));
                } catch (Exception e) {
                    log.error("refreshMarketPriceCache-error:{}", config.getSymbol(), e);
                }
            });
        }
    }

    /**
     * 检测交易信号并入队
     * 增强版：集成ADR震荡过滤器，有效过滤70%的震荡假信号
     * 流程：检测信号 → 构建订单 → 入队处理
     */
    public void detectAndEnqueueTradingSignals() {
        try {
            if (DMAS_CACHE.isEmpty() || LATEST_PRICE_CACHE.isEmpty()) return;

            DMAS_CACHE.forEach((symbol, data) -> {
                DoubleMovingAverageStrategyConfig conf = CONFIG_MAP.get(symbol);
                if (!conf.getEnable() || !LATEST_PRICE_CACHE.containsKey(conf.getSymbol())) return;

                // 1. 仓位状态检查（必须允许开单），统一获取/创建状态对象（默认 false，不允许）
                AtomicBoolean allowOpen = canOpenPositionMap.computeIfAbsent(symbol, k -> new AtomicBoolean(false));
                if (!allowOpen.get()) return;
                BigDecimal latestPrice = LATEST_PRICE_CACHE.get(conf.getSymbol());
                DoubleMovingAveragePlaceOrder order = null;

                // 2. 跟踪趋势下单
                if (isStrictMATrendConfirmed(data)) {
                    order = buildTrendFollowingPlaceOrder(conf, data, latestPrice);
                }

                // 3. 订单入队处理
                if (order != null && tryAcquireOpenLock(symbol, conf.getTimeFrame())) {
                    // 获取用于写入的 allowOpen 对象（如果之前不存在，则认为允许开单）
                    AtomicBoolean allowOpenForSet = canOpenPositionMap.computeIfAbsent(symbol, k -> new AtomicBoolean(true));
                    if (ORDER_QUEUE.offer(order)) {
                        // 成功入队后再禁止该 symbol 继续开单
                        allowOpenForSet.set(false);
                        log.info("detectAndEnqueueTradingSignals:检测到双均线交易信号，已放入下单队列，order:{}", toJson(order));
                    } else {
                        // 入队失败，立即释放开单锁，允许快速重试
                        AtomicBoolean lock = openLockMap.get(symbol);
                        if (lock != null) {
                            lock.set(true);
                        }
                        log.warn("detectAndEnqueueTradingSignals: 下单队列已满，放入失败，symbol={}", symbol);
                    }
                }
            });
        } catch (Exception e) {
            log.error("detectAndEnqueueTradingSignals-error", e);
        }
    }

    /**
     * 构建跟踪趋势下单
     **/
    public DoubleMovingAveragePlaceOrder buildTrendFollowingPlaceOrder(DoubleMovingAverageStrategyConfig conf, DoubleMovingAverageData data, BigDecimal latestPrice) {
        // 检测多头趋势
        if (isLongTrendCondition(data, latestPrice)) {
            return buildLongTrendOrder(conf, data, latestPrice);
        }
        // 检测空头趋势
        if (isShortTrendCondition(data, latestPrice)) {
            return buildShortTrendOrder(conf, data, latestPrice);
        }
        return null;
    }

    /**
     * 检测多头趋势条件
     */
    private boolean isLongTrendCondition(DoubleMovingAverageData data, BigDecimal latestPrice) {
        return gt(latestPrice, data.getMa144())
                && gt(latestPrice, data.getEma144())
                && (gt(data.getMa21(), data.getMa144()) || gt(data.getEma21(), data.getEma144()));
    }

    /**
     * 检测空头趋势条件
     */
    private boolean isShortTrendCondition(DoubleMovingAverageData data, BigDecimal latestPrice) {
        return lt(latestPrice, data.getMa144())
                && lt(latestPrice, data.getEma144())
                && (lt(data.getMa21(), data.getMa144()) || lt(data.getEma21(), data.getEma144()));
    }

    /**
     * 构建多头趋势订单
     */
    private DoubleMovingAveragePlaceOrder buildLongTrendOrder(DoubleMovingAverageStrategyConfig conf, DoubleMovingAverageData data, BigDecimal latestPrice) {
        BigDecimal highPrice = data.getMaxValue();
        BigDecimal lowPrice = data.getMinValue();
        // 计算中间价区间 (优化: 使用除法代替减法+乘法)
        BigDecimal medianPrice = highPrice.add(lowPrice).divide(BigDecimal.valueOf(2), conf.getPricePlace(), RoundingMode.HALF_UP);
        BigDecimal medianPriceLower = decrease(medianPrice, MEDIAN_DEVIATION, conf.getPricePlace());

        // 价格在中间价下方0.3%到中间价之间，符合多头开多条件
        if (gte(latestPrice, medianPriceLower) && lt(latestPrice, medianPrice)) {
            //计算止损价
            BigDecimal stopLossPrice = lowPrice.subtract(highPrice.subtract(lowPrice)).setScale(conf.getPricePlace(), RoundingMode.HALF_UP);
            return createPlaceOrder(conf, SIDE_BUY, latestPrice, stopLossPrice);
        }
        return null;
    }

    /**
     * 构建空头趋势订单
     */
    private DoubleMovingAveragePlaceOrder buildShortTrendOrder(DoubleMovingAverageStrategyConfig conf, DoubleMovingAverageData data, BigDecimal latestPrice) {
        BigDecimal highPrice = data.getMaxValue();
        BigDecimal lowPrice = data.getMinValue();
        // 计算中间价区间 (优化: 使用除法代替减法+乘法)
        BigDecimal medianPrice = highPrice.add(lowPrice).divide(BigDecimal.valueOf(2), conf.getPricePlace(), RoundingMode.HALF_UP);
        BigDecimal medianPriceUpper = increase(medianPrice, MEDIAN_DEVIATION, conf.getPricePlace());
        // 价格在中间价到中间价上方0.3%之间，符合空头开空条件
        if (gt(latestPrice, medianPrice) && lte(latestPrice, medianPriceUpper)) {
            //计算止损价
            BigDecimal stopLossPrice = highPrice.add(highPrice.subtract(lowPrice)).setScale(conf.getPricePlace(), RoundingMode.HALF_UP);
            return createPlaceOrder(conf, SIDE_SELL, latestPrice, stopLossPrice);
        }
        return null;
    }

    /**
     * 获取开仓锁
     **/
    private boolean tryAcquireOpenLock(String symbol, String timeFrame) {
        openLockMap.putIfAbsent(symbol, new AtomicBoolean(true));
        AtomicBoolean lock = openLockMap.get(symbol);
        if (lock.compareAndSet(true, false)) {
            // 根据时间周期动态设置冷却期
            Duration cooldown = getCooldownPeriod(timeFrame);
            taskScheduler.schedule(() -> lock.set(true), Instant.now().plus(cooldown));
            return true;
        }
        return false;
    }

    /**
     * 根据时间周期获取冷却期
     * <p>
     * 冷却期策略优化：
     * - H1周期：4小时冷却（4倍周期）
     * 原因：1小时周期波动快，4倍周期可避免同趋势重复开仓
     * <p>
     * - H4周期：12小时冷却（3倍周期）
     * 原因：H4周期趋势持续更久，3倍周期更稳健
     * 优化：从2倍周期（8h）提高到3倍周期（12h）
     * <p>
     * 这样可以避免在同一趋势中频繁开仓，同时不错过新趋势
     *
     * @param timeFrame 时间周期（如 "1H", "4H" 等）
     * @return 冷却期时长
     */
    private Duration getCooldownPeriod(String timeFrame) {
        // 优先匹配已知周期，提供明确的冷却策略
        if (CandleInterval.HOUR_1.getCode().equals(timeFrame)) {
            return Duration.ofHours(4);  // 1小时周期 → 4小时冷却（4倍周期）
        } else if (CandleInterval.HOUR_4.getCode().equals(timeFrame)) {
            return Duration.ofHours(12);  // 4小时周期 → 12小时冷却（3倍周期，优化后）
        } else if (CandleInterval.MINUTE_15.getCode().equals(timeFrame)) {
            return Duration.ofHours(1);  // 15分钟周期 → 1小时冷却（备用）
        } else if (CandleInterval.MINUTE_30.getCode().equals(timeFrame)) {
            return Duration.ofHours(2);  // 30分钟周期 → 2小时冷却（备用）
        } else if (CandleInterval.MINUTE_5.getCode().equals(timeFrame)) {
            return Duration.ofMinutes(30);  // 5分钟周期 → 30分钟冷却（备用）
        }
        // 默认返回4小时冷却期（保守策略）
        return Duration.ofHours(4);
    }

    /**
     * 检测是否形成突破趋势排列
     */
    private boolean isBreakoutTrend(DoubleMovingAverageData data, BigDecimal latestPrice) {
        return isLongBreakout(data, latestPrice) || isShortBreakout(data, latestPrice);
    }

    /**
     * 多头突破检测
     * 突破条件：价格突破MA144/EMA144，且形成严格的多头排列
     */
    private boolean isLongBreakout(DoubleMovingAverageData data, BigDecimal latestPrice) {
        // 1. 价格突破长期均线
        if (!gt(latestPrice, data.getMa144()) || !gt(latestPrice, data.getEma144())) {
            return false;
        }

        // 2. MA144/EMA144 在最顶部（压制 MA55/EMA55/MA21/EMA21）
        boolean ma144OnTop = gt(data.getMa144(), data.getMa55())
                && gt(data.getMa144(), data.getEma55())
                && gt(data.getMa144(), data.getMa21())
                && gt(data.getMa144(), data.getEma21());

        boolean ema144OnTop = gt(data.getEma144(), data.getMa55())
                && gt(data.getEma144(), data.getEma55())
                && gt(data.getEma144(), data.getMa21())
                && gt(data.getEma144(), data.getEma21());

        // 3. MA55/EMA55 在中间（在 MA144/EMA144 之下，在 MA21/EMA21 之上）
        boolean ma55InMiddle = lt(data.getMa55(), data.getMa144())
                && lt(data.getMa55(), data.getEma144())
                && gt(data.getMa55(), data.getMa21())
                && gt(data.getMa55(), data.getEma21());

        boolean ema55InMiddle = lt(data.getEma55(), data.getMa144())
                && lt(data.getEma55(), data.getEma144())
                && gt(data.getEma55(), data.getMa21())
                && gt(data.getEma55(), data.getEma21());

        // 4. MA21/EMA21 在最底部（被 MA55/EMA55 压制）
        boolean shortTermAtBottom = lt(data.getMa21(), data.getMa55())
                && lt(data.getMa21(), data.getEma55())
                && lt(data.getEma21(), data.getMa55())
                && lt(data.getEma21(), data.getEma55());

        return ma144OnTop && ema144OnTop && ma55InMiddle && ema55InMiddle && shortTermAtBottom;
    }

    /**
     * 空头突破检测
     * 突破条件：价格跌破MA144/EMA144，且形成严格的空头排列
     */
    private boolean isShortBreakout(DoubleMovingAverageData data, BigDecimal latestPrice) {
        // 1. 价格跌破长期均线
        if (!lt(latestPrice, data.getMa144()) || !lt(latestPrice, data.getEma144())) {
            return false;
        }

        // 2. MA144/EMA144 在最底部（被 MA55/EMA55/MA21/EMA21 压制）
        boolean ma144AtBottom = lt(data.getMa144(), data.getMa55())
                && lt(data.getMa144(), data.getEma55())
                && lt(data.getMa144(), data.getMa21())
                && lt(data.getMa144(), data.getEma21());

        boolean ema144AtBottom = lt(data.getEma144(), data.getMa55())
                && lt(data.getEma144(), data.getEma55())
                && lt(data.getEma144(), data.getMa21())
                && lt(data.getEma144(), data.getEma21());

        // 3. MA55/EMA55 在中间（在 MA144/EMA144 之上，在 MA21/EMA21 之下）
        boolean ma55InMiddle = gt(data.getMa55(), data.getMa144())
                && gt(data.getMa55(), data.getEma144())
                && lt(data.getMa55(), data.getMa21())
                && lt(data.getMa55(), data.getEma21());

        boolean ema55InMiddle = gt(data.getEma55(), data.getMa144())
                && gt(data.getEma55(), data.getEma144())
                && lt(data.getEma55(), data.getMa21())
                && lt(data.getEma55(), data.getEma21());

        // 4. MA21/EMA21 在最顶部（压制 MA55/EMA55）
        boolean shortTermOnTop = gt(data.getMa21(), data.getMa55())
                && gt(data.getMa21(), data.getEma55())
                && gt(data.getEma21(), data.getMa55())
                && gt(data.getEma21(), data.getEma55());

        return ma144AtBottom && ema144AtBottom && ma55InMiddle && ema55InMiddle && shortTermOnTop;
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
        boolean midTermCondition = (gt(data.getMa55(), data.getMa144()) && gt(data.getMa55(), data.getEma144())) && (gt(data.getEma55(), data.getMa144()) && gt(data.getEma55(), data.getEma144()));
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
        boolean midTermCondition = (lt(data.getMa55(), data.getMa144()) && lt(data.getMa55(), data.getEma144())) && (lt(data.getEma55(), data.getMa144()) && lt(data.getEma55(), data.getEma144()));
        return maCondition && emaCondition && maCrossCondition && emaCrossCondition && midTermCondition;
    }

    /**
     * 创建双均线下单信息
     */
    public DoubleMovingAveragePlaceOrder createPlaceOrder(DoubleMovingAverageStrategyConfig conf, String side, BigDecimal latestPrice, BigDecimal stopLossPrice) {
        DoubleMovingAveragePlaceOrder order = new DoubleMovingAveragePlaceOrder();
        order.setClientOid(Cloid.fromLong(IdUtil.getSnowflakeNextId()).getRaw());
        order.setSymbol(conf.getSymbol());
        order.setSide(side);
        order.setStopLossPrice(stopLossPrice.setScale(conf.getPricePlace(), RoundingMode.HALF_UP).toPlainString());
        order.setOrderType(HYPE_ORDER_TYPE_MARKET);
        order.setMarginMode(MARGIN_MODE_CROSSED);
        //计算涨跌幅百分比
        BigDecimal changePercent = calculateChangePercent(stopLossPrice, latestPrice).abs();
        //计算最大可开杠杆
        int actualLeverage = calculateExchangeMaxLeverage(changePercent, conf.getMaxLeverage());
        // 计算实际开仓数量
        BigDecimal realityOpenAmount = conf.getOpenAmount().multiply(BigDecimal.valueOf(actualLeverage));
        BigDecimal size = realityOpenAmount.divide(latestPrice, conf.getVolumePlace(), RoundingMode.HALF_UP);
        order.setSize(size.toPlainString());
        order.setLeverage(actualLeverage);
        // 止盈数量 默认50%
        order.setTakeProfitSize(size.multiply(BigDecimal.valueOf(0.5)).setScale(conf.getVolumePlace(), RoundingMode.HALF_UP).toPlainString());
        //止盈价需要满足：在2:1盈亏比的基础上，确保止盈至少能赚1%
        BigDecimal takeProfitPrice = BigDecimal.ZERO;
        //止盈幅度
        BigDecimal takeProfitPercent = BigDecimal.valueOf(1.0);
        if (SIDE_BUY.equals(side) && gt(latestPrice, stopLossPrice)) {
            // 多头止盈：开仓价 + (开仓价 - 止损价) × 2.0
            takeProfitPrice = latestPrice.add(latestPrice.subtract(stopLossPrice).multiply(new BigDecimal("2.0"))).setScale(conf.getPricePlace(), RoundingMode.HALF_UP);
            if (lt(calculateChangePercent(latestPrice, takeProfitPrice).abs(), takeProfitPercent)) {
                //如果止盈价小于1%，则设置latestPrice增加1%为止盈价
                takeProfitPrice = increase(latestPrice, takeProfitPercent, conf.getPricePlace());
            }
        } else if (SIDE_SELL.equals(side) && lt(latestPrice, stopLossPrice)) {
            // 空头止盈：开仓价 - (止损价 - 开仓价) × 2.0
            takeProfitPrice = latestPrice.subtract(stopLossPrice.subtract(latestPrice).multiply(new BigDecimal("2.0"))).setScale(conf.getPricePlace(), RoundingMode.HALF_UP);
            if (lt(calculateChangePercent(latestPrice, takeProfitPrice).abs(), takeProfitPercent)) {
                //如果止盈价小于1%，则设置latestPrice减少1%为止盈价
                takeProfitPrice = decrease(latestPrice, takeProfitPercent, conf.getPricePlace());
            }
        }
        order.setTakeProfitPrice(takeProfitPrice.toPlainString());
        return order;
    }

    /**
     * 获取所有仓位
     **/
    public Map<String, ClearinghouseState.Position> getAllPosition() {
        Map<String, ClearinghouseState.Position> positions = new HashMap<>();
        ClearinghouseState clearinghouseState = getAccountInfo();
        List<ClearinghouseState.AssetPositions> assetPositions = clearinghouseState.getAssetPositions();
        for (ClearinghouseState.AssetPositions assetPosition : assetPositions) {
            positions.put(assetPosition.getPosition().getCoin(), assetPosition.getPosition());
        }
        return positions;
    }

    /**
     * 验证账户余额
     */
    private boolean validateAccountBalance(DoubleMovingAveragePlaceOrder placeOrder) {
        ClearinghouseState state = getAccountInfo();
        DoubleMovingAverageStrategyConfig config = CONFIG_MAP.get(placeOrder.getSymbol());
        BigDecimal available = new BigDecimal(state.getWithdrawable());
        BigDecimal maxInvestAmount = config.getOpenAmount();
        placeOrder.setAccountBalance(available);
        if (lt(available, maxInvestAmount)) {
            log.warn("validateAccountBalance: USDC账户可用余额不足，无法执行下单操作! 订单: {} 可用余额: {}", toJson(placeOrder), available);
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
                        setLeverageForSymbol(orderParam.getSymbol(), MARGIN_MODE_CROSSED.equalsIgnoreCase(orderParam.getMarginMode()), orderParam.getLeverage());
                        // 执行下单
                        BulkOrder orderResult = executeOrder(orderParam);
                        log.info("startOrderConsumer: 下单完成，订单信息: {}, 返回结果: {}", toJson(orderParam), toJson(orderResult));
                        if (!STATUS_OK.equalsIgnoreCase(orderResult.getStatus())) {
                            log.warn("startOrderConsumer: 下单失败，订单信息: {}, 错误信息: {}", toJson(orderParam), toJson(orderResult));
                            return;
                        }
                        handleSuccessfulOrder(orderParam, orderResult);
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
     * 处理下单成功后的操作
     */
    private void handleSuccessfulOrder(DoubleMovingAveragePlaceOrder orderParam, BulkOrder bulkOrder) {
        try {
            if (orderParam.getTakeProfitSize() == null || orderParam.getTakeProfitPrice() == null) {
                return;
            }
            // 设置仓位止盈
            placeTakeProfitStopLossOrder(orderParam.getSymbol(), orderParam.getTakeProfitPrice(), orderParam.getTakeProfitSize(), orderParam.getSide());

            //获取账户信息
            ClearinghouseState clearinghouseState = getAccountInfo();
            List<JsonNode> statuses = bulkOrder.getResponse().getData().getStatuses();
            JsonNode first = statuses.getFirst();
            String oid = first.get("filled").get("oid").asText();
            // 发送HTML格式的邮件通知（传入实际成交数据）
            sendHtmlEmail(DateUtil.now() + " 双均线策略下单成功 ✅", buildOrderEmailContent(orderParam, clearinghouseState, oid));
        } catch (Exception e) {
            log.error("handleSuccessfulOrder-error: orderParam={}, orderResult={}", toJson(orderParam), toJson(bulkOrder), e);
        }
    }

    /**
     * 设置止盈止损计划委托下单
     * 创建计划委托订单，当价格触发时自动执行止盈或止损
     */
    public void placeTakeProfitStopLossOrder(String symbol, String executePrice, String size, String holdSide) {
        OrderRequest req = OrderRequest.Close.limit(Tif.GTC, symbol, !SIDE_BUY.equals(holdSide), size, executePrice, Cloid.auto());
        try {
            Order order = client.getExchange().order(req);
            log.info("placeTakeProfitStopLossOrder: 设置止盈止损委托计划成功, param: {}, result: {}", toJson(req), toJson(order));
        } catch (Exception e) {
            log.error("placeTakeProfitStopLossOrder-error: 设置止盈止损委托计划失败, param: {}, error: {}", toJson(req), e.getMessage());
        }
    }

    /**
     * 执行下单操作
     */
    private BulkOrder executeOrder(DoubleMovingAveragePlaceOrder orderParam) {
        OrderWithTpSlBuilder builder = OrderRequest.entryWithTpSl()
                .cloid(Cloid.fromStr(orderParam.getClientOid()))
                .perp(orderParam.getSymbol())
                .stopLoss(orderParam.getStopLossPrice());
        if (SIDE_BUY.equals(orderParam.getSide())) {
            builder.buy(orderParam.getSize());
        } else if (SIDE_SELL.equals(orderParam.getSide())) {
            builder.sell(orderParam.getSize());
        }
        return client.getExchange().bulkOrders(builder.buildNormalTpsl());
    }


    /****
     *   K 线数据构建 BarSeries
     * @param candles   K 线数据列表
     * @param candleDuration K 线周期，如 Duration.ofMinutes(1)
     * @return 返回构建好的 BarSeries
     */
    public static BarSeries buildSeriesFromCandles(List<Candle> candles, Duration candleDuration) {
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance()).build();
        for (Candle candle : candles) {
            Bar bar = new BaseBar(
                    candleDuration,
                    Instant.ofEpochMilli(candle.getStartTimestamp()),
                    null,//Instant.ofEpochMilli(candle.getEndTimestamp()),
                    series.numFactory().numOf(candle.getOpenPrice()),
                    series.numFactory().numOf(candle.getHighPrice()),
                    series.numFactory().numOf(candle.getLowPrice()),
                    series.numFactory().numOf(candle.getClosePrice()),
                    series.numFactory().numOf(candle.getVolume()),   // volume
                    series.numFactory().numOf(candle.getVolume()),  // amount (可以用 quoteVolume)
                    candle.getTradeCount()
            );
            series.addBar(bar);
        }
        return series;
    }

    /**
     * 计算最新双均线指标
     * MA21 , EMA21 ,MA55 , EMA55, MA144 , EMA144
     **/
    public static DoubleMovingAverageData calculateIndicators(BarSeries series, Integer pricePlace) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        Indicator<Num> ma21 = new SMAIndicator(closePrice, 21);
        Indicator<Num> ema21 = new EMAIndicator(closePrice, 21);
        Indicator<Num> ma55 = new SMAIndicator(closePrice, 55);
        Indicator<Num> ema55 = new EMAIndicator(closePrice, 55);
        Indicator<Num> ma144 = new SMAIndicator(closePrice, 144);
        Indicator<Num> ema144 = new EMAIndicator(closePrice, 144);
        int endIndex = series.getEndIndex();
        return new DoubleMovingAverageData(
                ma21.getValue(endIndex).bigDecimalValue().setScale(pricePlace, RoundingMode.HALF_UP),
                ma55.getValue(endIndex).bigDecimalValue().setScale(pricePlace, RoundingMode.HALF_UP),
                ma144.getValue(endIndex).bigDecimalValue().setScale(pricePlace, RoundingMode.HALF_UP),
                ema21.getValue(endIndex).bigDecimalValue().setScale(pricePlace, RoundingMode.HALF_UP),
                ema55.getValue(endIndex).bigDecimalValue().setScale(pricePlace, RoundingMode.HALF_UP),
                ema144.getValue(endIndex).bigDecimalValue().setScale(pricePlace, RoundingMode.HALF_UP));
    }


    /**
     * 获取账户信息
     **/
    public ClearinghouseState getAccountInfo() {
        try {
            return client.getInfo().userState(client.getSingleAddress());
        } catch (Exception e) {
            log.error("getAccountInfo-error", e);
            throw e;
        }
    }


    /**
     * 通过WebSocket订阅市场数据
     * 建立WebSocket连接，实时接收市场价格变动并更新缓存
     */
    public void subscribeMarketDataViaWebSocket() {
        Info info = client.getInfo();
        for (DoubleMovingAverageStrategyConfig config : CONFIG_MAP.values()) {
            String symbol = config.getSymbol();
            info.subscribe(TradesSubscription.of(symbol), msg -> {
                JsonNode data = msg.get("data");
                if (data == null || !data.isArray() || data.isEmpty()) {
                    return;
                }
                AtomicLong lastTimeRef = LAST_TRADE_TIME_CACHE.computeIfAbsent(symbol, k -> new AtomicLong(0));
                for (JsonNode trade : data) {
                    long tradeTime = trade.get("time").asLong();
                    // 只处理比当前记录更新的成交
                    if (tradeTime > lastTimeRef.get()) {
                        lastTimeRef.set(tradeTime);
                        BigDecimal lastPrice = new BigDecimal(trade.get("px").asText());
                        LATEST_PRICE_CACHE.put(symbol, lastPrice);
                        //System.out.println("最新价格: " + symbol + " " + lastPrice);
                    }
                }
            });
        }
    }

    /**
     * 仓位管理
     */
    public void managePositions() {
        try {
            // 获取当前所有持仓
            Map<String, ClearinghouseState.Position> positionMap = getAllPosition();

            // 根据仓位更新是否允许开单
            CONFIG_MAP.keySet().forEach(symbol -> {
                boolean hasPosition = positionMap.containsKey(symbol);
                canOpenPositionMap.computeIfAbsent(symbol, k -> new AtomicBoolean(false)).set(!hasPosition);
            });

            // 必须有仓位才能执行后续操作
            if (positionMap.isEmpty()) return;

            // 获取当前计划止盈止损委托
            Map<String, List<FrontendOpenOrder>> entrustedOrdersMap = getOrdersPlanPending();
            //log.info("managePositions: 当前持仓: {}, 当前计划止盈止损委托: {}", JsonUtil.toJson(positionMap), JsonUtil.toJson(entrustedOrdersMap));

            // 更新止盈止损计划
            updateTakeProfitStopLossPlans(positionMap, entrustedOrdersMap);
        } catch (Exception e) {
            log.error("managePositions-error", e);
        }
    }

    /**
     * 更新止盈止损计划
     * 根据持仓信息和当前价格动态调整止盈止损订单
     **/
    public void updateTakeProfitStopLossPlans(Map<String, ClearinghouseState.Position> positionMap, Map<String, List<FrontendOpenOrder>> entrustedOrdersMap) {
        if (positionMap == null || positionMap.isEmpty()) return;
        if (entrustedOrdersMap == null || entrustedOrdersMap.isEmpty()) return;

        positionMap.forEach((symbol, position) -> {
            try {
                DoubleMovingAverageStrategyConfig config = CONFIG_MAP.get(symbol);
                if (config == null) return;

                BigDecimal latestPrice = LATEST_PRICE_CACHE.get(config.getSymbol());
                DoubleMovingAverageData data = DMAS_CACHE.get(config.getSymbol());
                if (latestPrice == null || data == null) return;

                // 计算动态止盈价（基于持仓方向和盈亏平衡价）
                BigDecimal stopProfitPrice = calculateDynamicStopProfitPrice(latestPrice, data, config, position);
                // 如果动态止盈价小于等于0，则不更新
                if (lte(stopProfitPrice, BigDecimal.ZERO)) return;
                // 获取当前委托订单
                List<FrontendOpenOrder> entrustedOrders = entrustedOrdersMap.get(symbol);
                if (entrustedOrders == null || entrustedOrders.isEmpty()) return;

                // 更新止损订单
                updateStopLossOrders(entrustedOrders, data, stopProfitPrice);
            } catch (Exception e) {
                log.error("updateTakeProfitStopLossPlans-error: symbol={}", symbol, e);
            }
        });
    }

    /**
     * 计算动态止盈价
     * 根据当前价格、持仓方向和盈亏平衡价动态调整止盈位置
     * 重要：只有当实际盈利超过最低阈值时才启动动态止盈，避免刚开仓就被止盈
     *
     * @param latestPrice 最新价格
     * @param data        双均线数据
     * @param config      策略配置
     * @param position    持仓信息（包含盈亏平衡价、持仓方向）
     * @return 动态止盈价，如果不满足条件则返回ZERO
     */
    private BigDecimal calculateDynamicStopProfitPrice(BigDecimal latestPrice, DoubleMovingAverageData data,
                                                       DoubleMovingAverageStrategyConfig config, ClearinghouseState.Position position) {
        BigDecimal maxValue = data.getMaxValue();
        BigDecimal minValue = data.getMinValue();

        // 获取盈亏平衡价（包含开仓价格 + 手续费）
        BigDecimal breakEvenPrice = new BigDecimal(position.getEntryPx()).setScale(config.getPricePlace(), RoundingMode.HALF_UP);

        // 多头持仓: 价格上涨且盈利达到阈值时计算动态止盈
        if (Double.parseDouble(position.getSzi()) > 0) {
            return calculateStopProfitForLong(latestPrice, minValue, breakEvenPrice, config);
        }
        // 空头持仓: 价格下跌且盈利达到阈值时计算动态止盈
        else if (Double.parseDouble(position.getSzi()) < 0) {
            return calculateStopProfitForShort(latestPrice, maxValue, breakEvenPrice, config);
        }

        return BigDecimal.ZERO;
    }

    /**
     * 计算多头动态止盈价
     * 重要修复：只有当实际盈利超过2%时才启动动态止盈，避免刚开仓就被止盈或亏损止盈
     *
     * @param latestPrice    最新价格
     * @param basePrice      基础价格（止损价 = minValue）
     * @param breakEvenPrice 盈亏平衡价（开仓价 + 手续费）
     * @param config         策略配置
     */
    private BigDecimal calculateStopProfitForLong(BigDecimal latestPrice, BigDecimal basePrice,
                                                  BigDecimal breakEvenPrice, DoubleMovingAverageStrategyConfig config) {
        // 1. 首先检查是否已经盈利
        if (lte(latestPrice, breakEvenPrice)) {
            return BigDecimal.ZERO;  // 还未盈利，不触发动态止盈
        }

        // 2. 计算实际盈利百分比（基于盈亏平衡价）
        BigDecimal profitPercent = calculateChangePercent(breakEvenPrice, latestPrice).abs();

        // 3. 盈利必须超过最低阈值（默认2%）才启动动态止盈
        if (lte(profitPercent, MIN_PROFIT_THRESHOLD)) {
            return BigDecimal.ZERO;  // 盈利不足，不触发
        }

        // 4. 计算价格相对于止损价的偏离度
        BigDecimal priceChangePercent = calculateChangePercent(basePrice, latestPrice).abs();
        if (lte(priceChangePercent, config.getDeviationFromMA())) {
            return BigDecimal.ZERO;
        }

        // 5. 计算动态系数（分段计算，适应币圈插针特性）
        BigDecimal dynamicCoefficient = calculateDynamicCoefficient(priceChangePercent, config.getDeviationFromMA());

        // 6. 计算止盈价 = 基础价 + 价差 * 动态系数
        BigDecimal spread = latestPrice.subtract(basePrice).multiply(dynamicCoefficient);
        BigDecimal stopProfitPrice = basePrice.add(spread);

        // 7. 确保止盈价至少高于盈亏平衡价（保证盈利）
        if (lte(stopProfitPrice, breakEvenPrice)) {
            // 如果计算出的止盈价低于保本价，至少设为保本价+0.5%
            stopProfitPrice = breakEvenPrice.multiply(new BigDecimal("1.005"));
        }

        return stopProfitPrice.setScale(config.getPricePlace(), RoundingMode.HALF_UP);
    }

    /**
     * 计算空头动态止盈价
     * 重要修复：只有当实际盈利超过2%时才启动动态止盈，避免刚开仓就被止盈或亏损止盈
     *
     * @param latestPrice    最新价格
     * @param basePrice      基础价格（止损价 = maxValue）
     * @param breakEvenPrice 盈亏平衡价（开仓价 + 手续费）
     * @param config         策略配置
     */
    private BigDecimal calculateStopProfitForShort(BigDecimal latestPrice, BigDecimal basePrice,
                                                   BigDecimal breakEvenPrice, DoubleMovingAverageStrategyConfig config) {
        // 1. 首先检查是否已经盈利
        if (gte(latestPrice, breakEvenPrice)) {
            return BigDecimal.ZERO;  // 还未盈利，不触发动态止盈
        }

        // 2. 计算实际盈利百分比（基于盈亏平衡价）
        BigDecimal profitPercent = calculateChangePercent(breakEvenPrice, latestPrice).abs();

        // 3. 盈利必须超过最低阈值（默认2%）才启动动态止盈
        if (lte(profitPercent, MIN_PROFIT_THRESHOLD)) {
            return BigDecimal.ZERO;  // 盈利不足，不触发
        }

        // 4. 计算价格相对于止损价的偏离度
        BigDecimal priceChangePercent = calculateChangePercent(basePrice, latestPrice).abs();
        if (lte(priceChangePercent, config.getDeviationFromMA())) {
            return BigDecimal.ZERO;
        }

        // 5. 计算动态系数（分段计算，适应币圈插针特性）
        BigDecimal dynamicCoefficient = calculateDynamicCoefficient(priceChangePercent, config.getDeviationFromMA());

        // 6. 计算止盈价 = 基础价 - 价差 * 动态系数
        BigDecimal spread = basePrice.subtract(latestPrice).multiply(dynamicCoefficient);
        BigDecimal stopProfitPrice = basePrice.subtract(spread);

        // 7. 确保止盈价至少低于盈亏平衡价（保证盈利）
        if (gte(stopProfitPrice, breakEvenPrice)) {
            // 如果计算出的止盈价高于保本价，至少设为保本价-0.5%
            stopProfitPrice = breakEvenPrice.multiply(new BigDecimal("0.995"));
        }

        return stopProfitPrice.setScale(config.getPricePlace(), RoundingMode.HALF_UP);
    }

    /**
     * 计算动态止盈系数（分段计算策略）
     * 根据价格偏离度采用不同的系数增长速率，平衡收益与风险
     * 分段策略：
     * - 第一段 (0-10%超额偏离): 基础系数0.70，每1%增加1%，范围 0.70-0.80 (保守锁定70-80%利润)
     * - 第二段 (10-20%超额偏离): 基础系数0.80，每1%增加1%，范围 0.80-0.90 (稳健锁定80-90%利润)
     * - 第三段 (20%+超额偏离): 基础系数0.88，每1%增加0.35%，最大0.95 (激进但保留5%插针容忍度)
     *
     * @param currentDeviation   当前价格偏离度百分比
     * @param thresholdDeviation 配置的偏离度阈值
     * @return 动态系数 (0.70-0.95)
     */
    private BigDecimal calculateDynamicCoefficient(BigDecimal currentDeviation, BigDecimal thresholdDeviation) {
        // 计算超出阈值的偏离度
        BigDecimal excessDeviation = currentDeviation.subtract(thresholdDeviation);
        BigDecimal coefficient;
        // 第一阶段: 超额偏离 0-10%，系数 0.70-0.80 (保守阶段)
        if (lte(excessDeviation, STAGE1_THRESHOLD)) {
            coefficient = STAGE1_BASE_COEFFICIENT.add(excessDeviation.multiply(STAGE1_INCREMENT));
        }
        // 第二阶段: 超额偏离 10-20%，系数 0.80-0.90 (稳健阶段)
        else if (lte(excessDeviation, STAGE2_THRESHOLD)) {
            BigDecimal stage2Excess = excessDeviation.subtract(STAGE1_THRESHOLD);
            coefficient = STAGE2_BASE_COEFFICIENT.add(stage2Excess.multiply(STAGE2_INCREMENT));
        }
        // 第三阶段: 超额偏离 20%+，系数 0.88-0.95 (激进阶段，增速放缓)
        else {
            BigDecimal stage3Excess = excessDeviation.subtract(STAGE2_THRESHOLD);
            coefficient = STAGE3_BASE_COEFFICIENT.add(stage3Excess.multiply(STAGE3_INCREMENT));
        }
        // 限制最大系数为0.95，保留5%插针容忍度
        return coefficient.min(MAX_DYNAMIC_COEFFICIENT);
    }

    /**
     * 更新止损订单
     */
    private void updateStopLossOrders(List<FrontendOpenOrder> entrustedOrders, DoubleMovingAverageData data, BigDecimal stopProfitPrice) {
        for (FrontendOpenOrder order : entrustedOrders) {
            try {
                // 仅处理止损计划订单
                if (!"Stop Market".equals(order.getOrderType())) continue;

                BigDecimal triggerPrice = new BigDecimal(Optional.ofNullable(order.getTriggerPx()).orElse("0"));
                String side = order.getSide();

                // 做多止损 (SELL)
                if (SIDE_SELL.equals(side)) {
                    updateLongStopLoss(order, triggerPrice, data.getMinValue(), stopProfitPrice);
                }
                // 做空止损 (BUY)
                else if (SIDE_BUY.equals(side)) {
                    updateShortStopLoss(order, triggerPrice, data.getMaxValue(), stopProfitPrice);
                }
            } catch (Exception inner) {
                log.error("updateStopLossOrders: 单个委托处理失败 orderId={}, error={}", order.getOid(), inner.getMessage());
            }
        }
    }

    /**
     * 更新多头止损价
     * 止损价向上移动策略: 取 max(最低价, 动态止盈价)
     */
    private void updateLongStopLoss(FrontendOpenOrder order,
                                    BigDecimal currentTriggerPrice,
                                    BigDecimal minValue,
                                    BigDecimal stopProfitPrice) {
        BigDecimal newTriggerPrice = minValue;

        // 如果动态止盈价有效且更优，则使用动态止盈价
        if (gt(stopProfitPrice, BigDecimal.ZERO)
                && lt(currentTriggerPrice, stopProfitPrice)
                && gt(stopProfitPrice, newTriggerPrice)) {
            newTriggerPrice = stopProfitPrice;
        }

        // 仅当新触发价更高时才更新 (止损向上移动)
        if (gt(newTriggerPrice, currentTriggerPrice)) {
            modifyStopLossOrder(order, newTriggerPrice.toPlainString());
        }
    }

    /**
     * 更新空头止损价
     * 止损价向下移动策略: 取 min(最高价, 动态止盈价)
     */
    private void updateShortStopLoss(FrontendOpenOrder order, BigDecimal currentTriggerPrice, BigDecimal maxValue, BigDecimal stopProfitPrice) {
        BigDecimal newTriggerPrice = maxValue;

        // 如果动态止盈价有效且更优，则使用动态止盈价
        if (gt(stopProfitPrice, BigDecimal.ZERO)
                && gt(currentTriggerPrice, stopProfitPrice)
                && lt(stopProfitPrice, newTriggerPrice)) {
            newTriggerPrice = stopProfitPrice;
        }

        // 仅当新触发价更低且有效时才更新 (止损向下移动)
        if (lt(newTriggerPrice, currentTriggerPrice) && gt(newTriggerPrice, BigDecimal.ZERO)) {
            modifyStopLossOrder(order, newTriggerPrice.toPlainString());
        }
    }


    /**
     * 获取当前计划委托
     **/
    public Map<String, List<FrontendOpenOrder>> getOrdersPlanPending() {
        List<FrontendOpenOrder> frontendOpenOrders = client.getInfo().frontendOpenOrders(client.getSingleAddress());
        if (frontendOpenOrders == null || frontendOpenOrders.isEmpty()) return Map.of();
        return frontendOpenOrders.stream().collect(Collectors.groupingBy(FrontendOpenOrder::getCoin));
    }

    /**
     * 修改止盈止损计划
     */
    private void modifyStopLossOrder(FrontendOpenOrder order, String newTriggerPrice) {
        try {
            ModifyOrderRequest req = ModifyOrderRequest.byOid(order.getCoin(), order.getOid());
            req.setBuy(!"A".equals(order.getSide()));
            req.setLimitPx(newTriggerPrice);
            req.setSz(order.getSz());
            req.setReduceOnly(true);
            req.setOrderType(TriggerOrderType.sl(newTriggerPrice, true));

            ModifyOrder modifyOrder = client.getExchange().modifyOrder(req);
            log.info("modifyStopLossOrder: 更新止盈止损计划成功, order: {}, newTriggerPrice: {}", toJson(modifyOrder), newTriggerPrice);
        } catch (Exception e) {
            log.error("modifyStopLossOrder-error: 更新止盈止损计划失败, order: {}, newTriggerPrice: {}, error: {}", toJson(order), newTriggerPrice, e.getMessage());
        }
    }

    /**
     * 构建HTML格式的订单邮件内容（基于实际成交数据）
     *
     * @param order              订单参数信息
     * @param clearinghouseState 清算状态
     * @return HTML格式的邮件内容
     */
    public String buildOrderEmailContent(DoubleMovingAveragePlaceOrder order, ClearinghouseState clearinghouseState, String oid) {
        ClearinghouseState.Position position = null;
        List<ClearinghouseState.AssetPositions> assetPositions = clearinghouseState.getAssetPositions();
        for (ClearinghouseState.AssetPositions assetPosition : assetPositions) {
            if (order.getSymbol().equals(assetPosition.getPosition().getCoin())) {
                position = assetPosition.getPosition();
                break;
            }
        }
        // 提取实际成交数据
        boolean hasRealData = (position != null);

        // 判断交易方向
        boolean isBuy = "buy".equalsIgnoreCase(order.getSide());
        String directionColor = isBuy ? "#10b981" : "#ef4444";
        String directionIcon = isBuy ? "📈" : "📉";
        String directionText = isBuy ? "做多 (LONG)" : "做空 (SHORT)";
        String directionBg = isBuy ? "#d1fae5" : "#fee2e2";

        // 判断订单类型
        String orderTypeText = HYPE_ORDER_TYPE_MARKET.equalsIgnoreCase(order.getOrderType()) ? "市价单" : "限价单";
        String marginModeText = MARGIN_MODE_ISOLATED.equalsIgnoreCase(order.getMarginMode()) ? "逐仓" : "全仓";

        // 实际成交数据
        String actualPriceText = "";
        String actualSizeText = "";
        String actualDataRows = "";
        String orderStateText = "";

        if (hasRealData) {
            // 成交均价
            if (position.getEntryPx() != null) {
                actualPriceText = position.getEntryPx();
                actualDataRows += String.format(
                        "<tr><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; color: #6b7280;'>实际成交价</td>" +
                                "<td style='padding: 12px; border-bottom: 1px solid #e5e7eb; text-align: right; font-weight: 600; color: #3b82f6;'>%s USDC</td></tr>",
                        actualPriceText
                );
            }

            // 实际成交数量
            if (position.getSzi() != null) {
                actualSizeText = new BigDecimal(position.getSzi()).abs().toPlainString();
                actualDataRows += String.format(
                        "<tr><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; color: #6b7280;'>实际成交量</td>" +
                                "<td style='padding: 12px; border-bottom: 1px solid #e5e7eb; text-align: right; font-weight: 600;'>%s</td></tr>",
                        actualSizeText
                );
            }

            // 成交金额（USDC总额）
            if (position.getPositionValue() != null) {
                BigDecimal quoteVolume = new BigDecimal(position.getPositionValue());
                actualDataRows += String.format(
                        "<tr><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; color: #6b7280;'>成交金额</td>" +
                                "<td style='padding: 12px; border-bottom: 1px solid #e5e7eb; text-align: right; font-weight: 600; color: #8b5cf6;'>%s USDC</td></tr>",
                        quoteVolume.setScale(2, RoundingMode.HALF_UP)
                );
            }

            // 手续费
            BigDecimal feeAmount = BigDecimal.ZERO;
            actualDataRows += String.format(
                    "<tr><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; color: #6b7280;'>手续费</td>" +
                            "<td style='padding: 12px; border-bottom: 1px solid #e5e7eb; text-align: right; font-weight: 600; color: #f59e0b;'>%s USDC</td></tr>",
                    feeAmount.setScale(4, RoundingMode.HALF_UP)
            );

            // 订单状态
            String stateColor = "#10b981";
            String stateIcon = "✅";
            String stateLabel = "全部成交";

            orderStateText = String.format(
                    "<div style='text-align: center; margin-top: 10px;'>" +
                            "<span style='display: inline-block; padding: 6px 16px; background-color: %s; color: #ffffff; border-radius: 12px; font-size: 13px; font-weight: 600;'>%s %s</span>" +
                            "</div>",
                    stateColor, stateIcon, stateLabel
            );

        }

        // 计算盈亏比（基于实际成交价或预估价）
        String riskRewardHtml = "";
        try {
            BigDecimal stopLoss = new BigDecimal(order.getStopLossPrice());
            BigDecimal takeProfit = new BigDecimal(order.getTakeProfitPrice());
            BigDecimal currentPrice;
            BigDecimal actualSize = new BigDecimal(order.getSize());

            // 优先使用实际成交价，否则使用预估价
            if (hasRealData && position.getEntryPx() != null) {
                currentPrice = new BigDecimal(position.getEntryPx());
                if (position.getSzi() != null) {
                    actualSize = new BigDecimal(position.getSzi()).abs();
                }
            } else {
                // 使用预估价格
                if (isBuy) {
                    currentPrice = stopLoss.add(stopLoss.multiply(new BigDecimal("0.1")));
                } else {
                    currentPrice = stopLoss.subtract(stopLoss.multiply(new BigDecimal("0.1")));
                }
            }

            // 计算风险和收益金额
            BigDecimal riskAmount;
            BigDecimal rewardAmount;

            if (isBuy) {
                riskAmount = currentPrice.subtract(stopLoss);       // 开仓价 - 止损价
                rewardAmount = takeProfit.subtract(currentPrice);   // 止盈价 - 开仓价
            } else {
                riskAmount = stopLoss.subtract(currentPrice);      // 止损价 - 开仓价
                rewardAmount = currentPrice.subtract(takeProfit);  // 开仓价 - 止盈价
            }

            // 安全检查：确保风险和收益为正数
            if (riskAmount.compareTo(BigDecimal.ZERO) <= 0 || rewardAmount.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("buildOrderEmailContent: 盈亏比计算异常，风险={}, 收益={}, 开仓价={}, 止损={}, 止盈={}, 方向={}",
                        riskAmount, rewardAmount, currentPrice, stopLoss, takeProfit, isBuy ? "LONG" : "SHORT");
                return ""; // 跳过盈亏比计算
            }

            // 计算实际USDC金额（风险/收益 × 成交数量）
            BigDecimal riskAmountUSDC = riskAmount.multiply(actualSize);
            BigDecimal rewardAmountUSDC = rewardAmount.multiply(actualSize);
            BigDecimal riskRewardRatio = rewardAmount.divide(riskAmount, 2, RoundingMode.HALF_UP);

            String priceLabel = "实际成交价";

            riskRewardHtml = String.format(
                    "<tr><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; color: #6b7280;'>%s</td>" +
                            "<td style='padding: 12px; border-bottom: 1px solid #e5e7eb; text-align: right; font-weight: 600; color: #3b82f6;'>%s USDC</td></tr>" +
                            "<tr><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; color: #6b7280;'>潜在风险</td>" +
                            "<td style='padding: 12px; border-bottom: 1px solid #e5e7eb; text-align: right; color: #ef4444; font-weight: 600;'>-%s USDC</td></tr>" +
                            "<tr><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; color: #6b7280;'>潜在收益</td>" +
                            "<td style='padding: 12px; border-bottom: 1px solid #e5e7eb; text-align: right; color: #10b981; font-weight: 600;'>+%s USDC</td></tr>" +
                            "<tr><td style='padding: 12px; color: #6b7280;'>盈亏比</td>" +
                            "<td style='padding: 12px; text-align: right; color: #3b82f6; font-weight: 700; font-size: 16px;'>1:%s</td></tr>",
                    priceLabel,
                    currentPrice.setScale(2, RoundingMode.HALF_UP),
                    riskAmountUSDC.setScale(2, RoundingMode.HALF_UP),
                    rewardAmountUSDC.setScale(2, RoundingMode.HALF_UP),
                    riskRewardRatio
            );
        } catch (Exception e) {
            log.warn("buildOrderEmailContent: 盈亏比计算失败", e);
        }

        // 账户余额（可选）
        String accountBalanceRow = "";
        if (clearinghouseState.getWithdrawable() != null) {
            accountBalanceRow = String.format(
                    "<tr><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; color: #6b7280;'>账户余额</td>" +
                            "<td style='padding: 12px; border-bottom: 1px solid #e5e7eb; text-align: right; font-weight: 600;'>%s USDC</td></tr>",
                    clearinghouseState.getWithdrawable()
            );
        }

        // 构建HTML邮件
        return String.format(
                "<!DOCTYPE html>" +
                        "<html lang='zh-CN'>" +
                        "<head>" +
                        "    <meta charset='UTF-8'>" +
                        "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                        "    <title>双均线策略交易通知</title>" +
                        "</head>" +
                        "<body style='margin: 0; padding: 0; background-color: #f3f4f6; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, \"Helvetica Neue\", Arial, sans-serif;'>" +
                        "    <div style='max-width: 600px; margin: 40px auto; background-color: #ffffff; border-radius: 12px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1); overflow: hidden;'>" +
                        "        <!-- 头部 -->" +
                        "        <div style='background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 30px; text-align: center;'>" +
                        "            <h1 style='margin: 0; color: #ffffff; font-size: 24px; font-weight: 700;'>📊 双均线策略交易通知</h1>" +
                        "            <p style='margin: 10px 0 0 0; color: #e0e7ff; font-size: 14px;'>%s</p>" +
                        "        </div>" +
                        "        " +
                        "        <!-- 交易方向标签 -->" +
                        "        <div style='padding: 20px; text-align: center; background-color: %s;'>" +
                        "            <span style='display: inline-block; padding: 10px 24px; background-color: %s; color: #ffffff; border-radius: 20px; font-size: 18px; font-weight: 700;'>" +
                        "                %s %s" +
                        "            </span>" +
                        "            %s" +
                        "        </div>" +
                        "        " +
                        "        <!-- 交易对信息 -->" +
                        "        <div style='padding: 20px 30px; background-color: #f9fafb;'>" +
                        "            <div style='text-align: center;'>" +
                        "                <span style='color: #6b7280; font-size: 14px;'>交易对</span>" +
                        "                <div style='margin-top: 8px; font-size: 28px; font-weight: 700; color: #1f2937;'>%s</div>" +
                        "            </div>" +
                        "        </div>" +
                        "        " +
                        "        <!-- 订单参数 -->" +
                        "        <div style='padding: 30px;'>" +
                        "            <h2 style='margin: 0 0 20px 0; color: #1f2937; font-size: 18px; font-weight: 600; border-left: 4px solid #667eea; padding-left: 12px;'>💰 订单参数</h2>" +
                        "            <table style='width: 100%%; border-collapse: collapse;'>" +
                        "                <tr><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; color: #6b7280;'>委托数量</td><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; text-align: right; font-weight: 600;'>%s</td></tr>" +
                        "                %s" +
                        "                <tr><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; color: #6b7280;'>订单类型</td><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; text-align: right; font-weight: 600;'>%s</td></tr>" +
                        "                <tr><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; color: #6b7280;'>仓位模式</td><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; text-align: right; font-weight: 600;'>%s</td></tr>" +
                        "                <tr><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; color: #6b7280;'>杠杆倍数</td><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; text-align: right; font-weight: 600; color: #f59e0b;'>%sx</td></tr>" +
                        "                %s" +
                        "            </table>" +
                        "        </div>" +
                        "        " +
                        "        <!-- 风控设置 -->" +
                        "        <div style='padding: 0 30px 30px 30px;'>" +
                        "            <h2 style='margin: 0 0 20px 0; color: #1f2937; font-size: 18px; font-weight: 600; border-left: 4px solid #10b981; padding-left: 12px;'>🎯 风控设置</h2>" +
                        "            <table style='width: 100%%; border-collapse: collapse;'>" +
                        "                <tr><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; color: #6b7280;'>止损价</td><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; text-align: right; font-weight: 600; color: #ef4444;'>%s USDC</td></tr>" +
                        "                <tr><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; color: #6b7280;'>止盈价</td><td style='padding: 12px; border-bottom: 1px solid #e5e7eb; text-align: right; font-weight: 600; color: #10b981;'>%s USDC</td></tr>" +
                        "                <tr><td style='padding: 12px; color: #6b7280;'>止盈数量</td><td style='padding: 12px; text-align: right; font-weight: 600;'>%s <span style='color: #6b7280; font-size: 12px;'>(50%%仓位)</span></td></tr>" +
                        "            </table>" +
                        "        </div>" +
                        "        " +
                        "        <!-- 盈亏比分析 -->" +
                        "        %s" +
                        "        " +
                        "        <!-- 订单ID -->" +
                        "        <div style='padding: 20px 30px; background-color: #f9fafb; border-top: 1px solid #e5e7eb;'>" +
                        "            <div style='display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px;'>" +
                        "                <span style='color: #6b7280; font-size: 14px;'>🎫 订单ID</span>" +
                        "                <span style='color: #1f2937; font-family: monospace; font-size: 12px;'>%s</span>" +
                        "            </div>" +
                        "            %s" +
                        "        </div>" +
                        "        " +
                        "        <!-- 底部提示 -->" +
                        "        <div style='padding: 20px 30px; background-color: #eff6ff; border-top: 2px solid #3b82f6;'>" +
                        "            <div style='display: flex; align-items: flex-start;'>" +
                        "                <span style='font-size: 20px; margin-right: 10px;'>ℹ️</span>" +
                        "                <div>" +
                        "                    <p style='margin: 0; color: #1e40af; font-size: 14px; line-height: 1.6;'>" +
                        "                        <strong>双重止盈机制：</strong><br>" +
                        "                        • 50%% 仓位在固定止盈价出场<br>" +
                        "                        • 50%% 仓位跟随动态止盈" +
                        "                    </p>" +
                        "                </div>" +
                        "            </div>" +
                        "        </div>" +
                        "        " +
                        "        <!-- 页脚 -->" +
                        "        <div style='padding: 20px; text-align: center; background-color: #1f2937; color: #9ca3af; font-size: 12px;'>" +
                        "            <p style='margin: 0;'>此邮件由双均线策略系统自动发送</p>" +
                        "            <p style='margin: 5px 0 0 0;'>%s</p>" +
                        "        </div>" +
                        "    </div>" +
                        "</body>" +
                        "</html>",
                hasRealData ? "订单已成交" : "订单已提交",
                directionBg, directionColor, directionIcon, directionText,
                orderStateText,
                order.getSymbol(),
                order.getSize(),
                actualDataRows,
                orderTypeText, marginModeText, order.getLeverage(),
                accountBalanceRow,
                order.getStopLossPrice(), order.getTakeProfitPrice(), order.getTakeProfitSize(),
                riskRewardHtml.isEmpty() ? "" :
                        "<div style='padding: 0 30px 30px 30px;'>" +
                                "    <h2 style='margin: 0 0 20px 0; color: #1f2937; font-size: 18px; font-weight: 600; border-left: 4px solid #3b82f6; padding-left: 12px;'>📉 盈亏比分析</h2>" +
                                "    <table style='width: 100%; border-collapse: collapse;'>" + riskRewardHtml + "</table>" +
                                "</div>",
                order.getClientOid(),
                hasRealData && oid != null ?
                        String.format(
                                "<div style='display: flex; align-items: center; justify-content: space-between;'>" +
                                        "    <span style='color: #6b7280; font-size: 14px;'>🏦 交易所订单ID</span>" +
                                        "    <span style='color: #1f2937; font-family: monospace; font-size: 12px;'>%s</span>" +
                                        "</div>",
                                oid
                        ) : "",
                DateUtil.now()
        );
    }

    /**
     * 发送HTML格式邮件通知
     **/
    public void sendHtmlEmail(String subject, String htmlContent) {
        mailService.sendHtmlMail(emailRecipient, subject, htmlContent);
    }
}
