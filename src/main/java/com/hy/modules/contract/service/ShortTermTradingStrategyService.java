package com.hy.modules.contract.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
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
import com.hy.modules.contract.entity.RangePriceOrder;
import com.hy.modules.contract.entity.ShortTermPlaceOrderParam;
import com.hy.modules.contract.entity.ShortTermPrice;
import com.hy.modules.contract.entity.ShortTermTradingStrategyConfig;
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
 * 短线交易策略服务类 V1
 * 实现基于价格短线的自动化交易策略
 * <p>
 * 主要功能：
 * 1. K线数据监控和短线价格计算
 * 2. 实时行情数据监控
 * 3. 策略信号生成和订单执行
 * 4. 仓位管理和风险控制
 * 5. 止盈止损管理
 */
@Slf4j
//@Service
public class ShortTermTradingStrategyService {

    // ==================== 依赖注入 ====================

    /**
     * Bitget API服务
     */
    private final BitgetCustomService bitgetCustomService;
    private final BitgetCustomService.BitgetSession bitgetSession;

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
     * 短线价格缓存 - 存储各币种的短线价格信息
     */
    private final static Map<String, ShortTermPrice> SHORT_TERM_PRICE_CACHE = new ConcurrentHashMap<>();

    /**
     * 实时行情数据缓存 - 存储各币种的最新价格
     */
    private final static Map<String, BigDecimal> MARKET_PRICE_CACHE = new ConcurrentHashMap<>();

    /**
     * 订单队列 - 存储待执行的订单参数
     */
    private static final BlockingQueue<ShortTermPlaceOrderParam> ORDER_QUEUE = new LinkedBlockingQueue<>(1000);

    // ==================== 控制标志 ====================

    /**
     * 订单消费者启动标志 - 确保只启动一次
     */
    private final AtomicBoolean ORDER_CONSUMER_STARTED = new AtomicBoolean(false);

    // ==================== 常量配置 ====================

    /**
     * K线数据获取数量限制
     */
    private final static Integer KLINE_DATA_LIMIT = 300;


    /**
     * 延迟开单时间（毫秒）- 2小时
     */
    private final static Long DELAY_OPEN_TIME_MS = 3600000L * 2;

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
     * 开仓资金比例 - 1%
     */
    private final static BigDecimal OPEN_POSITION_RATIO = new BigDecimal("0.01");

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
    public final static Map<String, ShortTermTradingStrategyConfig> STRATEGY_CONFIG_MAP = new ConcurrentHashMap<>() {
        {
            // BTC配置：杠杆50倍，开仓金额2USDT，价格精度4位，数量精度1位
            put(SymbolEnum.BTCUSDT.getCode(), new ShortTermTradingStrategyConfig(true, SymbolEnum.BTCUSDT.getCode(), 50, BigDecimal.valueOf(2), 4, 1, BitgetEnum.M5, 2.0));
            // ETH配置：杠杆20倍，开仓金额5USDT，价格精度2位，数量精度2位
            put(SymbolEnum.ETHUSDT.getCode(), new ShortTermTradingStrategyConfig(true, SymbolEnum.ETHUSDT.getCode(), 20, BigDecimal.valueOf(5.0), 2, 2, BitgetEnum.M5, 2.0));
        }
    };

    /**
     * 延迟开单时间映射 - 控制各币种的开单频率
     */
    private final static Map<String, Long> DELAY_OPEN_TIME_MAP = STRATEGY_CONFIG_MAP.values().stream()
            .collect(Collectors.toMap(ShortTermTradingStrategyConfig::getSymbol, v -> 0L));

    public ShortTermTradingStrategyService(BitgetCustomService bitgetCustomService, MailService mailService, @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.bitgetCustomService = bitgetCustomService;
        this.mailService = mailService;
        this.taskExecutor = executor;
        this.bitgetSession = bitgetCustomService.use(BitgetAccountType.RANGE);
    }

    /**
     * 启动短线交易策略服务
     * 初始化账户配置、启动订单消费者、建立WebSocket连接
     */
    public void start() {
        // 初始化Bitget账户配置
        initializeBitgetAccount();
        // 启动订单消费者线程
        startOrderConsumer();
        // 建立WebSocket行情数据监控
        startWebSocketMarketDataMonitoring();
        log.info("短线交易策略服务启动完成, 当前配置: {}", JsonUtil.toJson(STRATEGY_CONFIG_MAP));
    }

    /**
     * 初始化Bitget账户配置
     * 设置杠杆、持仓模式和保证金模式等基础交易参数
     */
    public void initializeBitgetAccount() {
        try {
            for (ShortTermTradingStrategyConfig config : STRATEGY_CONFIG_MAP.values()) {
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
            ResponseResult<BitgetSetLeverageResp> rs = bitgetSession.setLeverage(
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
    private void setMarginModeForSymbol(ShortTermTradingStrategyConfig config) {
        try {
            ResponseResult<BitgetSetMarginModeResp> rs = bitgetSession.setMarginMode(
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
            ResponseResult<BitgetSetPositionModeResp> rs = bitgetSession.setPositionMode(
                    BG_PRODUCT_TYPE_USDT_FUTURES, BG_POS_MODE_ONE_WAY_MODE
            );
            log.info("setPositionMode-设置持仓模式成功: result={}", JsonUtil.toJson(rs));
        } catch (Exception e) {
            log.error("setPositionMode-设置持仓模式失败:", e);
        }
    }

    /**
     * 启动K线数据监控
     * 为每个启用的币种异步获取K线数据并计算短线价格
     */
    public void startKlineMonitoring() {
        for (ShortTermTradingStrategyConfig config : STRATEGY_CONFIG_MAP.values()) {
            taskExecutor.execute(() -> {
                try {
                    // 获取K线数据
                    ResponseResult<List<BitgetMixMarketCandlesResp>> rs = bitgetSession.getMinMarketCandles(
                            config.getSymbol(), BG_PRODUCT_TYPE_USDT_FUTURES, config.getGranularity().getCode(), KLINE_DATA_LIMIT
                    );
                    if (!BG_RESPONSE_CODE_SUCCESS.equals(rs.getCode()) || rs.getData() == null || rs.getData().isEmpty()) {
                        log.error("startKlineMonitoring-error: 获取K线数据失败, symbol: {}, rs: {}", config.getSymbol(), JsonUtil.toJson(rs));
                        return;
                    }
                    // 计算短线价格
                    calculateRangePrice(rs.getData(), config);
                } catch (Exception e) {
                    log.error("startKlineMonitoring-error: symbol={}", config.getSymbol(), e);
                }
            });
        }
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
     * 计算短线价格
     * 根据K线数据计算最高价、最低价、均价等关键价格指标
     *
     * @param candles K线数据列表
     * @param config  策略配置
     */
    public void calculateRangePrice(List<BitgetMixMarketCandlesResp> candles, ShortTermTradingStrategyConfig config) {
        if (candles.isEmpty()) return;

        //获取前10最高价,从阴线(最低价)中获取
        List<BitgetMixMarketCandlesResp> top10HighPrices = candles.stream()
                .filter(c -> lt(c.getClosePrice(), c.getOpenPrice()))
                .sorted(Comparator.comparing(BitgetMixMarketCandlesResp::getLowPrice).reversed())
                .limit(10).toList();

        //获取前10最低价,从阳线(最高价)中获取
        List<BitgetMixMarketCandlesResp> top10LowPrices = candles.stream()
                .filter(c -> gt(c.getClosePrice(), c.getOpenPrice()))
                .sorted(Comparator.comparing(BitgetMixMarketCandlesResp::getHighPrice))
                .limit(10).toList();

        BitgetMixMarketCandlesResp highPriceCandle = findMaxHighCandle(candles);
        BitgetMixMarketCandlesResp lowPriceCandle = findMinLowCandle(candles);

        // 计算前10高价的均价
        BigDecimal highPriceSum = top10HighPrices.stream().map(BitgetMixMarketCandlesResp::getLowPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal highPriceAvg = highPriceSum.divide(BigDecimal.valueOf(top10HighPrices.size()), 4, RoundingMode.HALF_UP);


        // 计算前10低价的均价
        BigDecimal lowPriceSum = top10LowPrices.stream().map(BitgetMixMarketCandlesResp::getHighPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal lowPriceAvg = lowPriceSum.divide(BigDecimal.valueOf(top10LowPrices.size()), 4, RoundingMode.HALF_UP);

        // 更新短线价格缓存
        SHORT_TERM_PRICE_CACHE.put(config.getSymbol(), new ShortTermPrice(
                config.getSymbol(),
                highPriceCandle.getTimestamp(), highPriceCandle.getHighPrice(),
                lowPriceCandle.getTimestamp(), lowPriceCandle.getLowPrice(),
                highPriceAvg, lowPriceAvg
        ));
    }

    /**
     * 启动行情数据监控
     * 通过REST API获取实时行情数据
     */
    public void startMarketDataMonitoring() {
        for (ShortTermTradingStrategyConfig config : STRATEGY_CONFIG_MAP.values()) {
            taskExecutor.execute(() -> {
                try {
                    ResponseResult<List<BitgetMixMarketTickerResp>> rs = bitgetSession.getMixMarketTicker(config.getSymbol(), BG_PRODUCT_TYPE_USDT_FUTURES);
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
     * 根据短线价格和当前行情生成交易信号
     */
    public void monitorTradingSignals() {
        try {
            if (SHORT_TERM_PRICE_CACHE.isEmpty()) return;

            for (ShortTermPrice shortTermPrice : SHORT_TERM_PRICE_CACHE.values()) {
                ShortTermTradingStrategyConfig config = STRATEGY_CONFIG_MAP.get(shortTermPrice.getSymbol());
                long currentTime = System.currentTimeMillis();

                Long delay = DELAY_OPEN_TIME_MAP.get(shortTermPrice.getSymbol());
                if (currentTime < delay ||
                        !config.getEnable() ||
                        !MARKET_PRICE_CACHE.containsKey(shortTermPrice.getSymbol())) {
                    continue;
                }

                ShortTermPlaceOrderParam order = generateOrderSignal(shortTermPrice, config.getPricePlace(), MARKET_PRICE_CACHE.get(shortTermPrice.getSymbol()));
                if (order == null) continue;

                if (ORDER_QUEUE.offer(order)) {
                    log.info("monitorTradingSignals: 队列添加订单成功, order: {}", JsonUtil.toJson(order));
                    DELAY_OPEN_TIME_MAP.put(shortTermPrice.getSymbol(), currentTime + DELAY_OPEN_TIME_MS); // 设置延迟开单时间
                }
            }
        } catch (Exception e) {
            log.error("monitorTradingSignals-error", e);
        }
    }

    /**
     * 生成订单信号
     * 根据当前价格和短线价格判断是否生成买卖信号
     *
     * @param pricePlace     价格精度
     * @param shortTermPrice 短线价格信息
     * @param latestPrice    最新价格
     * @return 订单参数，如果不满足条件则返回null
     */
    public ShortTermPlaceOrderParam generateOrderSignal(ShortTermPrice shortTermPrice, Integer pricePlace, BigDecimal latestPrice) {
        BigDecimal highPrice = shortTermPrice.getHighPrice();
        BigDecimal lowPrice = shortTermPrice.getLowPrice();
        BigDecimal highAveragePrice = shortTermPrice.getHighAveragePrice();
        BigDecimal lowAveragePrice = shortTermPrice.getLowAveragePrice();

        // 计算价格容忍区间
        BigDecimal upHighPrice = highAveragePrice.multiply(PRICE_TOLERANCE_UPPER).setScale(pricePlace, RoundingMode.HALF_UP);
        BigDecimal downHighPrice = highAveragePrice.multiply(PRICE_TOLERANCE_LOWER).setScale(pricePlace, RoundingMode.HALF_UP);
        BigDecimal upLowPrice = lowAveragePrice.multiply(PRICE_TOLERANCE_UPPER).setScale(pricePlace, RoundingMode.HALF_UP);
        BigDecimal downLowPrice = lowAveragePrice.multiply(PRICE_TOLERANCE_LOWER).setScale(pricePlace, RoundingMode.HALF_UP);

        ShortTermPlaceOrderParam order = new ShortTermPlaceOrderParam();
        order.setClientOid(IdUtil.getSnowflakeNextIdStr());
        order.setSymbol(shortTermPrice.getSymbol());
        order.setPrice(latestPrice);
        order.setOrderType(BG_ORDER_TYPE_MARKET);
        order.setMarginMode(BG_MARGIN_MODE_CROSSED);

        // 判断是否在卖出区间
        if (gte(latestPrice, downHighPrice) && lte(latestPrice, upHighPrice)) {
            //if (true) {
            BigDecimal presetStopLossPrice = highPrice.multiply(STOP_LOSS_UPPER_MULTIPLIER).setScale(pricePlace, RoundingMode.HALF_UP);
            order.setSide(BG_SIDE_SELL);
            order.setPresetStopLossPrice(presetStopLossPrice);
            return order;
        }

        // 判断是否在买入区间
        if (gte(latestPrice, downLowPrice) && lte(latestPrice, upLowPrice)) {
            //if (true) {
            BigDecimal presetStopLossPrice = lowPrice.multiply(STOP_LOSS_LOWER_MULTIPLIER).setScale(pricePlace, RoundingMode.HALF_UP);
            order.setSide(BG_SIDE_BUY);
            order.setPresetStopLossPrice(presetStopLossPrice);
            return order;
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
                        ShortTermPlaceOrderParam orderParam = ORDER_QUEUE.take(); // 阻塞直到有数据

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
        ShortTermTradingStrategyConfig config = STRATEGY_CONFIG_MAP.get(symbol);
        return calculateAndSetLeverage(symbol, config.getLeverage());
    }

    public Integer calculateAndSetLeverage(String symbol, Integer level) {
        Integer leverage = level;
        try {
            //是否允许增加杠杆
            if (leverageIncrease) {
                ResponseResult<List<BitgetHistoryPositionResp>> result = bitgetSession.getHistoryPosition(symbol, 100);
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
                        if (gte(new BigDecimal(hp.getNetProfit()), BigDecimal.ZERO)) break;
                        leverage += 1;
                    }
                }
            }
            // 限制最大杠杆倍数为100
            if (leverage > 100) {
                leverage = 100;
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
        List<BitgetAllPositionResp> positions = Optional.ofNullable(bitgetSession.getAllPosition().getData()).orElse(Collections.emptyList());
        return positions.stream().anyMatch(pos -> symbol.equals(pos.getSymbol()));
    }

    /**
     * 验证账户余额
     */
    private boolean validateAccountBalance(ShortTermPlaceOrderParam orderParam) {
        Map<String, BitgetAccountsResp> accountMap = getAccountInfo();
        BitgetAccountsResp accountsResp = accountMap.get(DEFAULT_CURRENCY_USDT);
        if (accountsResp == null) {
            log.warn("validateAccountBalance: 未获取到USDT账户信息，无法执行下单! 订单: {}", JsonUtil.toJson(orderParam));
            return false;
        }

        ShortTermTradingStrategyConfig config = STRATEGY_CONFIG_MAP.get(orderParam.getSymbol());
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
    private void calculateOrderParameters(ShortTermPlaceOrderParam orderParam, Integer leverage) {
        ShortTermTradingStrategyConfig config = STRATEGY_CONFIG_MAP.get(orderParam.getSymbol());
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
    private ResponseResult<BitgetPlaceOrderResp> executeOrder(ShortTermPlaceOrderParam orderParam) throws Exception {
        return bitgetSession.placeOrder(
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
    private void handleSuccessfulOrder(ShortTermPlaceOrderParam orderParam, BitgetPlaceOrderResp orderResult) {
        RangePriceOrder order = BeanUtil.toBean(orderParam, RangePriceOrder.class);
        order.setOrderId(orderResult.getOrderId());
        order.setClientOid(orderResult.getClientOid());

        log.info("handleSuccessfulOrder: 下单成功，订单信息:{} , Bitget订单信息:{}", JsonUtil.toJson(orderParam), JsonUtil.toJson(order));

        // 设置延迟开单时间
        DELAY_OPEN_TIME_MAP.put(orderParam.getSymbol(), System.currentTimeMillis() + DELAY_OPEN_TIME_MS);

        // 设置止损
        setStopLossOrder(orderParam.getSymbol(), orderParam.getPresetStopLossPrice(), null, null, orderParam.getSide(), BG_PLAN_TYPE_POS_LOSS);

        // 设置止盈
        setBatchTakeProfitOrders(orderResult.getOrderId(), orderParam);
    }

    /**
     * 设置分批止盈订单
     */
    public void setBatchTakeProfitOrders(String orderId, ShortTermPlaceOrderParam orderParam) {
        try {
            String symbol = orderParam.getSymbol();
            ResponseResult<BitgetOrderDetailResp> orderDetailResult = bitgetSession.getOrderDetail(symbol, orderId);
            if (!BG_RESPONSE_CODE_SUCCESS.equals(orderDetailResult.getCode()) || orderDetailResult.getData() == null) {
                log.error("setBatchTakeProfitOrders:获取订单详情失败，订单ID: {}, 错误信息: {}", orderId, JsonUtil.toJson(orderDetailResult));
                return;
            }
            ShortTermTradingStrategyConfig config = STRATEGY_CONFIG_MAP.get(symbol);
            BitgetOrderDetailResp orderDetail = orderDetailResult.getData();
            BigDecimal priceAvg = new BigDecimal(orderDetail.getPriceAvg());
            BigDecimal presetStopSurplusPrice = BigDecimal.ZERO;

            BigDecimal presetStopLossPrice = orderParam.getPresetStopLossPrice();
            String side = orderParam.getSide();
            Integer pricePlace = config.getPricePlace();

            //计算预设止盈价
            if (BG_SIDE_BUY.equals(side)) {
                //默认止盈价=开仓均价+(开仓均价-止损价)
                presetStopSurplusPrice = priceAvg.add(priceAvg.subtract(presetStopLossPrice)).setScale(pricePlace, RoundingMode.HALF_UP);
                //计算止盈盈亏比
                if (config.getTakeProfitProfitLossRatio() > 0) {
                    BigDecimal profitLossRatio = BigDecimal.valueOf(config.getTakeProfitProfitLossRatio());
                    presetStopSurplusPrice = priceAvg.add(priceAvg.subtract(presetStopLossPrice).multiply(profitLossRatio)).setScale(pricePlace, RoundingMode.HALF_UP);
                }
            } else if (BG_SIDE_SELL.equals(side)) {
                //默认止盈价=开仓均价-(止损价-开仓均价)
                presetStopSurplusPrice = priceAvg.subtract(presetStopLossPrice.subtract(priceAvg)).setScale(pricePlace, RoundingMode.HALF_UP);
                //计算止盈盈亏比
                if (config.getTakeProfitProfitLossRatio() > 0) {
                    BigDecimal profitLossRatio = BigDecimal.valueOf(config.getTakeProfitProfitLossRatio());
                    presetStopSurplusPrice = priceAvg.subtract(presetStopLossPrice.subtract(priceAvg).multiply(profitLossRatio)).setScale(pricePlace, RoundingMode.HALF_UP);
                }
            }

            // 设置仓位止盈
            setStopLossOrder(symbol, presetStopSurplusPrice, presetStopSurplusPrice, null, side, BG_PLAN_TYPE_POS_PROFIT);
        } catch (Exception e) {
            log.error("setBatchTakeProfitOrders-error: orderId={}", orderId, e);
        }
    }

    /**
     * 获取账户信息
     */
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
            ResponseResult<BitgetPlaceTpslOrderResp> rs = bitgetSession.placeTpslOrder(param);
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
            ResponseResult<List<BitgetAllPositionResp>> positionResp = bitgetSession.getAllPosition();
            if (!BG_RESPONSE_CODE_SUCCESS.equals(positionResp.getCode())) {
                log.error("managePositions-error: 获取仓位信息失败, rs: {}", JsonUtil.toJson(positionResp));
                return;
            }

            List<BitgetAllPositionResp> positions = Optional.ofNullable(positionResp.getData()).orElse(Collections.emptyList());
            // 必须有仓位才能执行后续操作
            if (positions.isEmpty()) return;

            Map<String, BitgetAllPositionResp> positionMap = positions.stream().collect(Collectors.toMap(BitgetAllPositionResp::getSymbol, p -> p, (existing, replacement) -> existing));

            // 获取当前计划止盈止损委托
            ResponseResult<BitgetOrdersPlanPendingResp> planResp = bitgetSession.getOrdersPlanPending(BG_PLAN_TYPE_PROFIT_LOSS, BG_PRODUCT_TYPE_USDT_FUTURES);
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

            // 如果有仓位，延迟开单时间设置为当前时间 + 2小时
            DELAY_OPEN_TIME_MAP.replaceAll((symbol, oldDelay) -> {
                BitgetAllPositionResp pr = positionMap.get(symbol);
                List<BitgetOrdersPlanPendingResp.EntrustedOrder> ppr = entrustedOrdersMap.get(symbol);
                if (pr != null && ppr != null && !ppr.isEmpty()) {
                    //ppr 获取仓位止损
                    BitgetOrdersPlanPendingResp.EntrustedOrder eo = ppr.stream().filter(o -> BG_PLAN_TYPE_POS_LOSS.equals(o.getPlanType())).findFirst().orElse(null);
                    if (eo != null) {
                        ShortTermTradingStrategyConfig config = STRATEGY_CONFIG_MAP.get(symbol);
                        BigDecimal openPriceAvg = new BigDecimal(pr.getOpenPriceAvg()).setScale(config.getPricePlace(), RoundingMode.HALF_UP);
                        BigDecimal triggerPrice = new BigDecimal(eo.getTriggerPrice());
                        String side = eo.getSide();

                        //做多 sell 卖 做空 buy 买
                        if ((BG_SIDE_SELL.equals(side) && gt(openPriceAvg, triggerPrice)) ||
                                (BG_SIDE_BUY.equals(side) && lt(openPriceAvg, triggerPrice))) {
                            return System.currentTimeMillis() + DELAY_OPEN_TIME_MS;
                        }
                    }
                }
                return oldDelay;
            });
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

                ShortTermTradingStrategyConfig config = STRATEGY_CONFIG_MAP.get(symbol);
                if (config == null) return;

                BigDecimal openPriceAvg = new BigDecimal(position.getOpenPriceAvg()).setScale(config.getPricePlace(), RoundingMode.HALF_UP);
                BigDecimal latestPrice = MARKET_PRICE_CACHE.get(symbol);

                for (BitgetOrdersPlanPendingResp.EntrustedOrder order : orders) {
                    BigDecimal triggerPrice = new BigDecimal(order.getTriggerPrice());
                    String planType = order.getPlanType();
                    String side = order.getSide();
                    //仓位止损
                    if (BG_PLAN_TYPE_POS_LOSS.equals(planType) && latestPrice != null) {
                        //做多 sell 卖
                        if (BG_SIDE_SELL.equals(side)) {
                            //设置保本损
                            BigDecimal percentage = openPriceAvg.multiply(new BigDecimal("1.008")).setScale(config.getPricePlace(), RoundingMode.HALF_UP);
                            BigDecimal newTriggerPrice = openPriceAvg.multiply(new BigDecimal("1.002")).setScale(config.getPricePlace(), RoundingMode.HALF_UP);
                            if (ne(triggerPrice, newTriggerPrice) && lte(percentage, latestPrice)) {
                                modifyStopLossOrder(order, newTriggerPrice, null, "");
                            }
                        }
                        //做空 buy 买
                        else if (BG_SIDE_BUY.equals(side)) {
                            //设置保本损
                            BigDecimal percentage = openPriceAvg.multiply(new BigDecimal("0.992")).setScale(config.getPricePlace(), RoundingMode.HALF_UP);
                            BigDecimal newTriggerPrice = openPriceAvg.multiply(new BigDecimal("0.998")).setScale(config.getPricePlace(), RoundingMode.HALF_UP);
                            if (ne(triggerPrice, newTriggerPrice) && gte(percentage, latestPrice)) {
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
            ResponseResult<BitgetPlaceTpslOrderResp> result = bitgetSession.modifyTpslOrder(param);
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
        for (ShortTermTradingStrategyConfig config : STRATEGY_CONFIG_MAP.values()) {
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
     * 发送短线价格信息邮件
     * 定时发送HTML格式的短线价格报告
     */
    public void sendRangePriceEmail() {
        if (SHORT_TERM_PRICE_CACHE.isEmpty()) return;

        try {
            StringBuilder content = new StringBuilder();
            content.append("<html><body>");
            content.append("<h2>📊 ").append(DateUtil.formatDateTime(new Date())).append("短线价格信息报告</h2>");
            content.append("<table border='1' cellpadding='8' cellspacing='0' style='border-collapse:collapse;'>");
            content.append("<thead><tr>")
                    .append("<th>币种</th>")
                    .append("<th>最高均价</th>")
                    .append("<th>最低均价</th>")
                    .append("<th>最高价</th>")
                    .append("<th>最高价时间</th>")
                    .append("<th>最低价</th>")
                    .append("<th>最低价时间</th>")
                    .append("</tr></thead>");
            content.append("<tbody>");

            for (ShortTermPrice shortTermPrice : SHORT_TERM_PRICE_CACHE.values()) {
                content.append("<tr>")
                        .append("<td>").append(shortTermPrice.getSymbol()).append("</td>")
                        .append("<td>").append(shortTermPrice.getHighAveragePrice()).append("</td>")
                        .append("<td>").append(shortTermPrice.getLowAveragePrice()).append("</td>")
                        .append("<td>").append(shortTermPrice.getHighPrice()).append("</td>")
                        .append("<td>").append(DateUtil.formatDateTime(new Date(shortTermPrice.getHighPriceTimestamp()))).append("</td>")
                        .append("<td>").append(shortTermPrice.getLowPrice()).append("</td>")
                        .append("<td>").append(DateUtil.formatDateTime(new Date(shortTermPrice.getLowPriceTimestamp()))).append("</td>")
                        .append("</tr>");
            }
            content.append("</tbody></table>");
            content.append("<p style='color:gray;font-size:12px;'>此邮件为系统自动发送，请勿回复。</p>");
            content.append("</body></html>");

            // 发送HTML邮件
            mailService.sendHtmlMail(emailRecipient, DateUtil.now() + " 短线价格信息", content.toString());
        } catch (Exception e) {
            log.error("sendRangePriceEmail-error:", e);
        }
    }

}