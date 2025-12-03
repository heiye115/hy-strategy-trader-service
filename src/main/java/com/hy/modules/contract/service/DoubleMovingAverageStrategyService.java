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
     * 中间价偏离度 (0.3%)
     **/
    private final static BigDecimal MEDIAN_DEVIATION = new BigDecimal("0.3");

    /**
     * 动态止盈分段系数 - 第一段基础系数 (保守阶段)
     * 偏离度在阈值基础上 0-10% 时的基础系数
     **/
    private final static BigDecimal STAGE1_BASE_COEFFICIENT = new BigDecimal("0.70");

    /**
     * 动态止盈分段系数 - 第二段基础系数 (稳健阶段)
     * 偏离度在阈值基础上 10-20% 时的基础系数
     **/
    private final static BigDecimal STAGE2_BASE_COEFFICIENT = new BigDecimal("0.80");

    /**
     * 动态止盈分段系数 - 第三段基础系数 (激进阶段)
     * 偏离度在阈值基础上 20%+ 时的基础系数
     **/
    private final static BigDecimal STAGE3_BASE_COEFFICIENT = new BigDecimal("0.88");

    /**
     * 动态止盈系数最大值 (保留5%插针容忍度)
     **/
    private final static BigDecimal MAX_DYNAMIC_COEFFICIENT = new BigDecimal("0.95");

    /**
     * 动态止盈最低盈利阈值 (1%)
     * 只有当实际盈利超过此阈值时，才启动动态止盈机制
     **/
    private final static BigDecimal MIN_PROFIT_THRESHOLD = new BigDecimal("2.0");

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
     **/
    private final static Map<String, DoubleMovingAverageStrategyConfig> CONFIG_MAP = new ConcurrentHashMap<>() {
        {
            put(SymbolEnum.BTCUSDT.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.BTCUSDT.getCode(), BitgetEnum.H1.getCode(), 4, 1, 100, BigDecimal.valueOf(10.0), BigDecimal.valueOf(8.0)));
            put(SymbolEnum.ETHUSDT.getCode(), new DoubleMovingAverageStrategyConfig(true, SymbolEnum.ETHUSDT.getCode(), BitgetEnum.H1.getCode(), 2, 2, 100, BigDecimal.valueOf(10.0), BigDecimal.valueOf(12.0)));
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
                    // 建议改为更友好的错误处理
                    BitgetEnum bitgetEnum = BitgetEnum.getByCode(config.getTimeFrame());
                    if (bitgetEnum == null) {
                        log.error("doubleMovingAverageDataMonitoring: 未知的时间周期, symbol={}, timeFrame={}", config.getSymbol(), config.getTimeFrame());
                        return;
                    }
                    BarSeries barSeries = buildSeriesFromBitgetCandles(rs.getData(), bitgetEnum.getDuration());
                    DoubleMovingAverageData data = calculateIndicators(barSeries, config.getPricePlace());
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
                BigDecimal latestPrice = BTR_CACHE.get(conf.getSymbol());
                DoubleMovingAveragePlaceOrder order = null;
                //跟踪趋势下单
                if (isStrictMATrendConfirmed(data)) {
                    order = buildTrendFollowingPlaceOrder(conf, data, latestPrice);
                }
                //跟踪突破下单
                if (order == null && isBreakoutTrend(data, latestPrice)) {
                    order = buildBreakoutPlaceOrder(conf, data, latestPrice);
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
        BigDecimal medianPriceLower = AmountCalculator.decrease(medianPrice, MEDIAN_DEVIATION, conf.getPricePlace());

        // 价格在中间价下方0.3%到中间价之间，符合多头开多条件
        if (gte(latestPrice, medianPriceLower) && lt(latestPrice, medianPrice)) {
            return createPlaceOrder(conf, BG_SIDE_BUY, latestPrice, lowPrice);
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
        BigDecimal medianPriceUpper = AmountCalculator.increase(medianPrice, MEDIAN_DEVIATION, conf.getPricePlace());

        // 价格在中间价到中间价上方0.3%之间，符合空头开空条件
        if (gt(latestPrice, medianPrice) && lte(latestPrice, medianPriceUpper)) {
            return createPlaceOrder(conf, BG_SIDE_SELL, latestPrice, highPrice);
        }

        return null;
    }

    /**
     * 构建跟踪突破下单
     **/
    public DoubleMovingAveragePlaceOrder buildBreakoutPlaceOrder(DoubleMovingAverageStrategyConfig conf, DoubleMovingAverageData data, BigDecimal latestPrice) {
        BigDecimal maxValue = data.getMaxValue();
        BigDecimal minValue = data.getMinValue();

        // 多头突破: 价格突破最高位
        if (gt(latestPrice, maxValue)) {
            return createPlaceOrder(conf, BG_SIDE_BUY, latestPrice, minValue);
        }

        // 空头突破: 价格跌破最低位
        if (lt(latestPrice, minValue)) {
            return createPlaceOrder(conf, BG_SIDE_SELL, latestPrice, maxValue);
        }

        return null;
    }


    /**
     * 获取开仓锁
     **/
    private boolean tryAcquireOpenLock(String symbol) {
        openLockMap.putIfAbsent(symbol, new AtomicBoolean(true));
        AtomicBoolean lock = openLockMap.get(symbol);
        if (lock.compareAndSet(true, false)) {
            // 根据时间周期动态设置冷却期
            DoubleMovingAverageStrategyConfig config = CONFIG_MAP.get(symbol);
            Duration cooldown = getCooldownPeriod(config.getTimeFrame());
            taskScheduler.schedule(() -> lock.set(true), Instant.now().plus(cooldown));
            return true;
        }
        return false;
    }

    /**
     * 根据时间周期获取冷却期
     * 冷却期策略：短周期（1小时）使用4倍周期作为冷却期，长周期（4小时）使用2倍周期
     * 这样可以避免在同一趋势中频繁开仓，同时不错过新趋势
     *
     * @param timeFrame 时间周期（如 "1H", "4H" 等）
     * @return 冷却期时长
     */
    private Duration getCooldownPeriod(String timeFrame) {
        // 优先匹配已知周期，提供明确的冷却策略
        if (BitgetEnum.H1.getCode().equals(timeFrame)) {
            return Duration.ofHours(4);  // 1小时周期 → 4小时冷却（4倍周期）
        } else if (BitgetEnum.H4.getCode().equals(timeFrame)) {
            return Duration.ofHours(8);  // 4小时周期 → 8小时冷却（2倍周期）
        } else if (BitgetEnum.M15.getCode().equals(timeFrame)) {
            return Duration.ofHours(1);  // 15分钟周期 → 1小时冷却（备用）
        } else if (BitgetEnum.M30.getCode().equals(timeFrame)) {
            return Duration.ofHours(2);  // 30分钟周期 → 2小时冷却（备用）
        }

        // 默认返回4小时冷却期（保守策略）
        return Duration.ofHours(4);
    }

    /**
     * 检测是否形成突破趋势排列
     **/
    private boolean isBreakoutTrend(DoubleMovingAverageData data, BigDecimal latestPrice) {
        //多头突破
        if (gt(latestPrice, data.getMa144()) &&
                gt(latestPrice, data.getEma144()) &&

                gt(data.getMa144(), data.getMa55()) &&
                gt(data.getMa144(), data.getEma55()) &&
                gt(data.getMa144(), data.getMa21()) &&
                gt(data.getMa144(), data.getEma21()) &&

                gt(data.getEma144(), data.getMa55()) &&
                gt(data.getEma144(), data.getEma55()) &&
                gt(data.getEma144(), data.getMa21()) &&
                gt(data.getEma144(), data.getEma21())

        ) {
            return true;
        }
        //空头突破
        return lt(latestPrice, data.getMa144()) &&
                lt(latestPrice, data.getEma144()) &&

                lt(data.getMa144(), data.getMa55()) &&
                lt(data.getMa144(), data.getEma55()) &&
                lt(data.getMa144(), data.getMa21()) &&
                lt(data.getMa144(), data.getEma21()) &&

                lt(data.getEma144(), data.getMa55()) &&
                lt(data.getEma144(), data.getEma55()) &&
                lt(data.getEma144(), data.getMa21()) &&
                lt(data.getEma144(), data.getEma21());
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
    public DoubleMovingAveragePlaceOrder createPlaceOrder(DoubleMovingAverageStrategyConfig conf, String side, BigDecimal latestPrice, BigDecimal stopLossPrice) {
        DoubleMovingAveragePlaceOrder order = new DoubleMovingAveragePlaceOrder();
        order.setClientOid(IdUtil.getSnowflakeNextIdStr());
        order.setSymbol(conf.getSymbol());
        order.setSide(side);
        order.setStopLossPrice(stopLossPrice.setScale(conf.getPricePlace(), RoundingMode.HALF_UP).toPlainString());
        order.setOrderType(BG_ORDER_TYPE_MARKET);
        order.setMarginMode(BG_MARGIN_MODE_ISOLATED);
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
        //止盈价 盈亏比 1:1
        BigDecimal takeProfitPrice = BigDecimal.ZERO;
        if (BG_SIDE_BUY.equals(side) && gt(latestPrice, stopLossPrice)) {
            takeProfitPrice = latestPrice.add(latestPrice.subtract(stopLossPrice)).setScale(conf.getPricePlace(), RoundingMode.HALF_UP);
        } else if (BG_SIDE_SELL.equals(side) && lt(latestPrice, stopLossPrice)) {
            takeProfitPrice = latestPrice.subtract(stopLossPrice.subtract(latestPrice)).setScale(conf.getPricePlace(), RoundingMode.HALF_UP);
        }
        order.setTakeProfitPrice(takeProfitPrice.toPlainString());
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
                        if (orderResult.getData() == null) {
                            log.error("startOrderConsumer: 下单失败，订单信息: {}, 错误信息: {}", JsonUtil.toJson(orderParam), JsonUtil.toJson(orderResult));
                            continue;
                        }
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
     * 处理下单成功后的操作
     */
    private void handleSuccessfulOrder(DoubleMovingAveragePlaceOrder orderParam, BitgetPlaceOrderResp orderResult) {
        try {
            if (orderParam.getTakeProfitSize() == null || orderParam.getTakeProfitPrice() == null) {
                return;
            }
            // 设置仓位止盈
            setPlaceTpslOrder(orderParam.getSymbol(), orderParam.getTakeProfitPrice(), orderParam.getTakeProfitPrice(), orderParam.getTakeProfitSize(), orderParam.getSide(), BG_PLAN_TYPE_PROFIT_PLAN);
        } catch (Exception e) {
            log.error("handleSuccessfulOrder-error: orderParam={}, orderResult={}", JsonUtil.toJson(orderParam), JsonUtil.toJson(orderResult), e);
        }
    }

    /**
     * 设置止盈止损计划委托下单
     */
    public void setPlaceTpslOrder(String symbol, String triggerPrice, String executePrice, String size, String holdSide, String planType) {
        BitgetPlaceTpslOrderParam param = new BitgetPlaceTpslOrderParam();
        param.setClientOid(IdUtil.getSnowflakeNextIdStr());
        param.setMarginCoin(DEFAULT_CURRENCY_USDT);
        param.setProductType(BG_PRODUCT_TYPE_USDT_FUTURES);
        param.setSymbol(symbol);
        param.setPlanType(planType);
        param.setTriggerType(BG_TRIGGER_TYPE_FILL_PRICE);
        param.setTriggerPrice(triggerPrice);
        if (executePrice != null) {
            param.setExecutePrice(executePrice);
        }
        if (size != null) {
            param.setSize(size);
        }
        param.setHoldSide(holdSide);
        try {
            ResponseResult<BitgetPlaceTpslOrderResp> rs = bitgetSession.placeTpslOrder(param);
            if (rs == null) {
                log.error("setPlaceTpslOrder: 设置止盈止损委托计划失败, param: {}", JsonUtil.toJson(param));
                return;
            }
            log.info("setPlaceTpslOrder: 设置止盈止损委托计划成功, param: {}, result: {}", JsonUtil.toJson(param), JsonUtil.toJson(rs));
        } catch (Exception e) {
            log.error("setPlaceTpslOrder-error: 设置止盈止损委托计划失败, param: {}, error: {}", JsonUtil.toJson(param), e.getMessage());
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
                orderParam.getStopLossPrice());
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

                // 计算动态止盈价（基于持仓方向和盈亏平衡价）
                BigDecimal stopProfitPrice = calculateDynamicStopProfitPrice(latestPrice, data, config, position);

                // 获取当前委托订单
                List<BitgetOrdersPlanPendingResp.EntrustedOrder> entrustedOrders = entrustedOrdersMap.get(symbol);
                if (entrustedOrders == null || entrustedOrders.isEmpty()) return;

                // 更新止损订单
                updateStopLossOrders(entrustedOrders, data, stopProfitPrice);
            } catch (Exception e) {
                log.error("updateTpslPlans-error: symbol={}", symbol, e);
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
                                                       DoubleMovingAverageStrategyConfig config, BitgetAllPositionResp position) {
        BigDecimal maxValue = data.getMaxValue();
        BigDecimal minValue = data.getMinValue();

        // 获取盈亏平衡价（包含开仓价格 + 手续费）
        BigDecimal breakEvenPrice = new BigDecimal(position.getBreakEvenPrice()).setScale(config.getPricePlace(), RoundingMode.HALF_UP);

        // 多头持仓: 价格上涨且盈利达到阈值时计算动态止盈
        if (BG_HOLD_SIDE_LONG.equals(position.getHoldSide())) {
            return calculateStopProfitForLong(latestPrice, minValue, breakEvenPrice, config);
        }
        // 空头持仓: 价格下跌且盈利达到阈值时计算动态止盈
        else if (BG_HOLD_SIDE_SHORT.equals(position.getHoldSide())) {
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
    private void updateStopLossOrders(List<BitgetOrdersPlanPendingResp.EntrustedOrder> entrustedOrders,
                                      DoubleMovingAverageData data,
                                      BigDecimal stopProfitPrice) {
        for (BitgetOrdersPlanPendingResp.EntrustedOrder order : entrustedOrders) {
            try {
                // 仅处理止损计划订单
                if (!BG_PLAN_TYPE_LOSS_PLAN.equals(order.getPlanType())) continue;

                BigDecimal triggerPrice = new BigDecimal(Optional.ofNullable(order.getTriggerPrice()).orElse("0"));
                String side = order.getSide();

                // 做多止损 (SELL)
                if (BG_SIDE_SELL.equals(side)) {
                    updateLongStopLoss(order, triggerPrice, data.getMinValue(), stopProfitPrice);
                }
                // 做空止损 (BUY)
                else if (BG_SIDE_BUY.equals(side)) {
                    updateShortStopLoss(order, triggerPrice, data.getMaxValue(), stopProfitPrice);
                }
            } catch (Exception inner) {
                log.error("updateStopLossOrders: 单个委托处理失败 orderId={}, error={}", order.getOrderId(), inner.getMessage());
            }
        }
    }

    /**
     * 更新多头止损价
     * 止损价向上移动策略: 取 max(最低价, 动态止盈价)
     */
    private void updateLongStopLoss(BitgetOrdersPlanPendingResp.EntrustedOrder order,
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
            modifyStopLossOrder(order, newTriggerPrice);
        }
    }

    /**
     * 更新空头止损价
     * 止损价向下移动策略: 取 min(最高价, 动态止盈价)
     */
    private void updateShortStopLoss(BitgetOrdersPlanPendingResp.EntrustedOrder order,
                                     BigDecimal currentTriggerPrice,
                                     BigDecimal maxValue,
                                     BigDecimal stopProfitPrice) {
        BigDecimal newTriggerPrice = maxValue;

        // 如果动态止盈价有效且更优，则使用动态止盈价
        if (gt(stopProfitPrice, BigDecimal.ZERO)
                && gt(currentTriggerPrice, stopProfitPrice)
                && lt(stopProfitPrice, newTriggerPrice)) {
            newTriggerPrice = stopProfitPrice;
        }

        // 仅当新触发价更低且有效时才更新 (止损向下移动)
        if (lt(newTriggerPrice, currentTriggerPrice) && gt(newTriggerPrice, BigDecimal.ZERO)) {
            modifyStopLossOrder(order, newTriggerPrice);
        }
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
