package com.hy.modules.contract.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.bitget.custom.entity.*;
import com.bitget.openapi.dto.request.ws.SubscribeReq;
import com.bitget.openapi.dto.response.ResponseResult;
import com.hy.common.enums.BitgetAccountType;
import com.hy.common.enums.BitgetEnum;
import com.hy.common.enums.SymbolEnum;
import com.hy.common.service.BitgetCustomService;
import com.hy.common.utils.json.JsonUtil;
import com.hy.modules.contract.entity.DualMovingAverageOrder;
import com.hy.modules.contract.entity.DualMovingAveragePlaceOrder;
import com.hy.modules.contract.entity.DualMovingAverageSignal;
import com.hy.modules.contract.entity.DualMovingAverageStrategyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
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
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.hy.common.constants.BitgetConstant.*;

/****
 * 双均线策略服务类
 * 该类负责处理双均线策略的相关逻辑，包括计算指标、持仓信息管理等。
 */
@Slf4j
//@Service
public class DualMovingAverageStrategyService {

    /**
     * Bitget自定义服务类
     **/
    private final BitgetCustomService bitgetCustomService;

    private final BitgetCustomService.BitgetSession bitgetSession;

    /**
     * 线程池
     **/
    private final TaskExecutor taskExecutor;

    /**
     * 双均线平均价格
     **/
    private final static Map<String, BigDecimal> dmasMap = new ConcurrentHashMap<>();

    /**
     * Bitget行情数据缓存
     **/
    private final static Map<String, BigDecimal> btrMap = new ConcurrentHashMap<>();

    /**
     * 双均线下单信息队列
     **/
    private static final BlockingQueue<DualMovingAveragePlaceOrder> orderQueue = new LinkedBlockingQueue<>(1000);


    /**
     * startOrderConsumer方法只允许启动一次
     **/
    private final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * K线数据的数量限制
     **/
    private final static Integer LIMIT = 500;

    /**
     * 策略配置
     **/
    private final static Map<String, DualMovingAverageStrategyConfig> configMap = new ConcurrentHashMap<>() {
        {
            put(SymbolEnum.BTCUSDT.getCode(), new DualMovingAverageStrategyConfig(true, SymbolEnum.BTCUSDT.getCode(), 0.3, 5, BigDecimal.valueOf(50.0), 4, 1, BitgetEnum.M5.getCode(), 2.0, 30.0, 4.0, 30.0));
            put(SymbolEnum.ETHUSDT.getCode(), new DualMovingAverageStrategyConfig(true, SymbolEnum.ETHUSDT.getCode(), 1.0, 2, BigDecimal.valueOf(50.0), 2, 2, BitgetEnum.H4.getCode(), 5.0, 30.0, 15.0, 30.0));
            put(SymbolEnum.SOLUSDT.getCode(), new DualMovingAverageStrategyConfig(true, SymbolEnum.SOLUSDT.getCode(), 2.0, 2, BigDecimal.valueOf(50.0), 1, 3, BitgetEnum.H4.getCode(), 10.0, 30.0, 20.0, 30.0));
        }
    };

    /**
     * 是否允许开单 , 有仓位的情况下禁止开单
     **/
    //private final static Map<String, Boolean> allowOpenMap = configMap.values().stream().collect(Collectors.toMap(DualMovingAverageStrategyConfig::getSymbol, v -> false));

    private static final Map<String, AtomicBoolean> allowOpenMap = configMap.values().stream()
            .collect(Collectors.toMap(DualMovingAverageStrategyConfig::getSymbol, v -> new AtomicBoolean(false)));

    public DualMovingAverageStrategyService(BitgetCustomService bitgetCustomService, @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.bitgetCustomService = bitgetCustomService;
        this.taskExecutor = executor;
        this.bitgetSession = bitgetCustomService.use(BitgetAccountType.RANGE);
    }

    public void start() {
        //设置Bitget账户信息
        setBitgetAccount();
        //启动下单消费者
        startOrderConsumer();
        //WS行情数据监控
        marketDataWSMonitoring();
        log.info("双均线策略加载完成, 当前配置: {}", JsonUtil.toJson(configMap));
    }

    /****
     * 设置Bitget账户信息
     * 设置杠杆、持仓模式和保证金模式等
     */
    public void setBitgetAccount() {
        try {
            for (DualMovingAverageStrategyConfig config : configMap.values()) {
                if (!config.getEnable()) continue;
                try {
                    // 设置杠杆
                    ResponseResult<BitgetSetLeverageResp> rs = bitgetSession.setLeverage(config.getSymbol(), BG_PRODUCT_TYPE_USDT_FUTURES, DEFAULT_CURRENCY_USDT, config.getLeverage().toString(), null);
                    log.info("setBitgetAccount-设置杠杆:{}", JsonUtil.toJson(rs));
                } catch (Exception e) {
                    log.error("setBitgetAccount-setLeverage报错:{}", config.getSymbol(), e);
                }
                try {
                    // 设置保证金模式
                    ResponseResult<BitgetSetMarginModeResp> rs = bitgetSession.setMarginMode(config.getSymbol(), BG_PRODUCT_TYPE_USDT_FUTURES, DEFAULT_CURRENCY_USDT, BG_MARGIN_MODE_CROSSED);
                    log.info("setBitgetAccount-设置保证金模式:{}", JsonUtil.toJson(rs));
                } catch (Exception e) {
                    log.error("setBitgetAccount-setMarginMode报错:{}", config.getSymbol(), e);
                }
            }
            // 设置持仓模式
            ResponseResult<BitgetSetPositionModeResp> rs = bitgetSession.setPositionMode(BG_PRODUCT_TYPE_USDT_FUTURES, BG_POS_MODE_ONE_WAY_MODE);
            log.info("setBitgetAccount-设置持仓模式:{}", JsonUtil.toJson(rs));
        } catch (Exception e) {
            log.error("setBitgetAccount-error:", e);
        }
    }

    /**
     * 双均线监控
     **/
    public void dualMovingAverageSignalMonitoring() {
        for (DualMovingAverageStrategyConfig config : configMap.values()) {
            taskExecutor.execute(() -> {
                try {
                    ResponseResult<List<BitgetMixMarketCandlesResp>> rs = bitgetSession.getMinMarketCandles(config.getSymbol(), BG_PRODUCT_TYPE_USDT_FUTURES, config.getTimeFrame(), LIMIT);
                    if (rs.getData() == null || rs.getData().isEmpty()) return;
                    BarSeries barSeries = buildSeriesFromBitgetCandles(rs.getData(), Objects.requireNonNull(BitgetEnum.getByCode(config.getTimeFrame())).getDuration());
                    List<Num> numList = calculateIndicators(barSeries);
                    BigDecimal average = (numList.get(0).bigDecimalValue().add(numList.get(1).bigDecimalValue())).divide(BigDecimal.TWO, config.getPricePlace(), RoundingMode.HALF_UP);
                    dmasMap.put(config.getSymbol(), average);
                } catch (Exception e) {
                    log.error("dualMovingAverageSignalMonitoring-error:{}", config.getSymbol(), e);
                }
            });
        }
    }

    /**
     * 行情数据监控
     */
    public void marketDataMonitoring() {
        for (DualMovingAverageStrategyConfig config : configMap.values()) {
            taskExecutor.execute(() -> {
                try {
                    ResponseResult<List<BitgetMixMarketTickerResp>> rs = bitgetSession.getMixMarketTicker(config.getSymbol(), BG_PRODUCT_TYPE_USDT_FUTURES);
                    if (rs.getData() == null || rs.getData().isEmpty()) return;
                    btrMap.put(config.getSymbol(), new BigDecimal(rs.getData().getFirst().getLastPr()));
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
            if (dmasMap.isEmpty()) return;
            dmasMap.forEach((symbol, averagePr) -> {
                DualMovingAverageStrategyConfig conf = configMap.get(symbol);
                if (allowOpenMap.get(symbol).compareAndSet(true, false)) {
                    // 已经是 false 了，直接跳过
                    return;
                }
                if (btrMap.containsKey(symbol) &&
                        conf.getEnable()) {
                    DualMovingAveragePlaceOrder order = preprocessPlaceOrder(conf, averagePr, btrMap.get(symbol));
                    if (order != null && orderQueue.offer(order)) {
                        log.info("signalOrderMonitoring: 队列添加订单成功, order: {}", JsonUtil.toJson(order));
                    }
                }
            });
        } catch (Exception e) {
            log.error("signalOrderMonitoring-error", e);
        }
    }

    /**
     * 预处理双均线下单信息
     */
    public DualMovingAveragePlaceOrder preprocessPlaceOrder(DualMovingAverageStrategyConfig conf, BigDecimal averagePr, BigDecimal latestPrice) {
        DualMovingAverageSignal dmas = detectMovingAverageCluster(averagePr, latestPrice, conf.getMinPercentThreshold());
        BigDecimal upHighPrice = dmas.getHighPrice().multiply(new BigDecimal("1.0005")).setScale(conf.getPricePlace(), RoundingMode.HALF_UP);
        BigDecimal downHighPrice = dmas.getHighPrice().multiply(new BigDecimal("0.9995")).setScale(conf.getPricePlace(), RoundingMode.HALF_UP);
        BigDecimal upLowPrice = dmas.getLowPrice().multiply(new BigDecimal("1.0005")).setScale(conf.getPricePlace(), RoundingMode.HALF_UP);
        BigDecimal downLowPrice = dmas.getLowPrice().multiply(new BigDecimal("0.9995")).setScale(conf.getPricePlace(), RoundingMode.HALF_UP);
        DualMovingAveragePlaceOrder order = new DualMovingAveragePlaceOrder();
        order.setClientOid(IdUtil.getSnowflakeNextIdStr());
        order.setSymbol(conf.getSymbol());
        order.setPrice(latestPrice);
        order.setOrderType(BG_ORDER_TYPE_MARKET);
        order.setMarginMode(BG_MARGIN_MODE_CROSSED);
        // 判断 latestPrice 是否在 [downHighPrice, upHighPrice] 范围内
        if (latestPrice.compareTo(downHighPrice) >= 0 && latestPrice.compareTo(upHighPrice) <= 0) {
            //if (true) {
            order.setSide(BG_SIDE_BUY);
            order.setPresetStopLossPrice(dmas.getLowPrice().setScale(conf.getPricePlace(), RoundingMode.HALF_UP).toPlainString());
            return order;
        }
        // 判断 latestPrice 是否在 [downLowPrice, upLowPrice] 范围内
        if (latestPrice.compareTo(downLowPrice) >= 0 && latestPrice.compareTo(upLowPrice) <= 0) {
            //if (true) {
            order.setSide(BG_SIDE_SELL);
            order.setPresetStopLossPrice(dmas.getHighPrice().setScale(conf.getPricePlace(), RoundingMode.HALF_UP).toPlainString());
            return order;
        }
        return null;
    }

    /**
     * 执行下单逻辑
     */
    public void startOrderConsumer() {
        if (started.compareAndSet(false, true)) {
            taskExecutor.execute(() -> {
                while (true) {
                    try {
                        DualMovingAveragePlaceOrder dmapo = orderQueue.take(); // 阻塞直到有数据

                        // 校验当前是否已有仓位
                        List<BitgetAllPositionResp> positions = Optional.ofNullable(bitgetSession.getAllPosition().getData()).orElse(Collections.emptyList());
                        boolean hasPosition = positions.stream().anyMatch(pos -> dmapo.getSymbol().equals(pos.getSymbol()));
                        //当前已有仓位存在，无法执行下单操作!
                        if (hasPosition) continue;


                        // 校验账户余额
                        Map<String, BitgetAccountsResp> barMap = getAccountInfo();
                        BitgetAccountsResp accountsResp = barMap.get(DEFAULT_CURRENCY_USDT);
                        if (accountsResp == null) {
                            log.warn("startOrderConsumer: 未获取到USDT账户信息，无法执行下单!");
                            continue;
                        }

                        DualMovingAverageStrategyConfig conf = configMap.get(dmapo.getSymbol());
                        BigDecimal available = new BigDecimal(accountsResp.getAvailable());
                        BigDecimal crossedMaxAvailable = new BigDecimal(accountsResp.getCrossedMaxAvailable());
                        BigDecimal openAmount = conf.getOpenAmount();

                        if (available.compareTo(openAmount) <= 0) {
                            log.warn("startOrderConsumer: USDT账户可用余额不足，无法执行下单操作! 可用余额: {}", available);
                            continue;
                        }

                        // 计算开仓金额（取初始值 or 比例值）
                        BigDecimal proportionAmount = available.multiply(new BigDecimal("0.25")).setScale(2, RoundingMode.HALF_UP);
                        if (proportionAmount.compareTo(openAmount) > 0 && crossedMaxAvailable.compareTo(proportionAmount) >= 0) {
                            openAmount = proportionAmount;
                        }

                        // 计算实际开仓数量
                        BigDecimal realityOpenAmount = openAmount.multiply(BigDecimal.valueOf(conf.getLeverage()));
                        BigDecimal size = realityOpenAmount.divide(dmapo.getPrice(), conf.getVolumePlace(), RoundingMode.HALF_UP);
                        dmapo.setSize(size.toPlainString());

                        log.info("startOrderConsumer: 准备下单，dmapo:{}", JsonUtil.toJson(dmapo));

                        //ResponseResult<BitgetPlaceOrderResp> bpor = null;
                        // 下单请求
                        ResponseResult<BitgetPlaceOrderResp> bpor = bitgetSession.placeOrder(
                                dmapo.getClientOid(),
                                dmapo.getSymbol(),
                                dmapo.getSize(),
                                dmapo.getSide(),
                                dmapo.getTradeSide(),
                                dmapo.getOrderType(),
                                dmapo.getMarginMode()
                        );

                        if (bpor == null || !BG_RESPONSE_CODE_SUCCESS.equals(bpor.getCode()) || bpor.getData() == null) {
                            log.error("startOrderConsumer: 下单失败，订单信息: {}, 错误信息: {}", JsonUtil.toJson(dmapo), JsonUtil.toJson(bpor));
                            continue;
                        }
                        BitgetPlaceOrderResp bpOrder = bpor.getData();
                        DualMovingAverageOrder order = BeanUtil.toBean(dmapo, DualMovingAverageOrder.class);
                        order.setOrderId(bpOrder.getOrderId());
                        order.setClientOid(bpOrder.getClientOid());
                        log.info("startOrderConsumer: 下单成功，订单信息:{}", JsonUtil.toJson(order));

                        //设置止损
                        setPlaceTpslOrder(dmapo.getSymbol(), dmapo.getPresetStopLossPrice(), null, null, dmapo.getSide(), BG_PLAN_TYPE_POS_LOSS);
                        // 设置分批止盈
                        setBatchTakeProfit(order.getOrderId(), dmapo, conf);
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
     * 设置分批止盈
     **/
    public void setBatchTakeProfit(String orderId, DualMovingAveragePlaceOrder dmapo, DualMovingAverageStrategyConfig conf) {
        try {
            ResponseResult<BitgetOrderDetailResp> odrs = bitgetSession.getOrderDetail(dmapo.getSymbol(), orderId);
            if (!BG_RESPONSE_CODE_SUCCESS.equals(odrs.getCode()) || odrs.getData() == null) {
                log.error("startOrderConsumer:获取订单详情失败，订单ID: {}, 错误信息: {}", orderId, JsonUtil.toJson(odrs));
                return;
            }

            BitgetOrderDetailResp orderDetail = odrs.getData();
            BigDecimal openPriceAvg = new BigDecimal(orderDetail.getPriceAvg());
            BigDecimal total = new BigDecimal(orderDetail.getBaseVolume());
            boolean isLong = BG_SIDE_BUY.equals(dmapo.getSide());

            // 数组形式处理两个止盈配置
            Double[] tpPercents = {conf.getTakeProfitPercent1(), conf.getTakeProfitPercent2()};
            Double[] tpPosPercents = {conf.getTakeProfitPositionPercent1(), conf.getTakeProfitPositionPercent2()};

            for (int i = 0; i < tpPercents.length; i++) {
                Double tpPercent = tpPercents[i];
                Double tpPosPercent = tpPosPercents[i];

                if (tpPercent == null || tpPercent <= 0 || tpPosPercent == null || tpPosPercent <= 0) continue;

                // 止盈价 = 平均价 * (1 ± tpPercent / 100.0)
                double ratio = tpPercent / 100.0;
                BigDecimal priceMultiplier = BigDecimal.valueOf(isLong ? (1.0 + ratio) : (1.0 - ratio));
                BigDecimal takeProfitPrice = openPriceAvg.multiply(priceMultiplier).setScale(conf.getPricePlace(), RoundingMode.HALF_UP);

                // 止盈仓位 = 总仓位 * (tpPosPercent / 100.0)
                BigDecimal takeProfitPosition = total.multiply(BigDecimal.valueOf(tpPosPercent / 100.0)).setScale(conf.getVolumePlace(), RoundingMode.HALF_UP);

                if (takeProfitPrice.compareTo(BigDecimal.ZERO) > 0 && takeProfitPosition.compareTo(BigDecimal.ZERO) > 0) {
                    setPlaceTpslOrder(dmapo.getSymbol(), takeProfitPrice.toPlainString(), takeProfitPrice.toPlainString(), takeProfitPosition.toPlainString(), dmapo.getSide(), BG_PLAN_TYPE_PROFIT_PLAN);
                }
            }
        } catch (Exception e) {
            log.error("takeProfitSetting报错:{}", orderId, e);
        }
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
                    series.numFactory().numOf(candle.getOpenPrice()),
                    series.numFactory().numOf(candle.getHighPrice()),
                    series.numFactory().numOf(candle.getLowPrice()),
                    series.numFactory().numOf(candle.getClosePrice()),
                    series.numFactory().numOf(candle.getBaseVolume()),   // volume
                    series.numFactory().numOf(candle.getQuoteVolume()),  // amount (可以用 quoteVolume)
                    0L                                           // trades，Bitget不提供可设0
            );
            series.addBar(bar);
        }
        return series;
    }

    /**
     * 计算双均线指标
     *
     * @param series K线数据
     * @return 返回最新的2条均线指标值
     */
    public static List<Num> calculateIndicators(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        Indicator<Num> ma144 = new SMAIndicator(closePrice, 144);
        Indicator<Num> ema144 = new EMAIndicator(closePrice, 144);
        int endIndex = series.getEndIndex();
        List<Num> numList = new ArrayList<>();
        numList.add(ma144.getValue(endIndex));
        numList.add(ema144.getValue(endIndex));
        numList.sort(Num::compareTo); // 从小到大排序
        return numList;
    }

    /**
     * 判断均线是否聚合，如果聚合且最新价格突破上下边界，则扩展价格通道。
     *
     * @param averagePr           均线均价
     * @param latestPrice         最新收盘价
     * @param minPercentThreshold 聚合判定的最小百分比阈值（例如：1.0 表示 1%）
     * @return DualMovingAverageSignal 包含高低价格区间
     */
    public DualMovingAverageSignal detectMovingAverageCluster(BigDecimal averagePr, BigDecimal latestPrice, double minPercentThreshold) {
        BigDecimal threshold = BigDecimal.valueOf(minPercentThreshold);
        DualMovingAverageSignal signal = new DualMovingAverageSignal();
        signal.setHighPrice(averagePr);
        signal.setLowPrice(averagePr);

        BigDecimal percentFactor = threshold.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        if (latestPrice.compareTo(averagePr) > 0) {
            BigDecimal newLow = averagePr.multiply(BigDecimal.ONE.subtract(percentFactor));
            signal.setLowPrice(newLow);
        } else {
            BigDecimal newHigh = averagePr.multiply(BigDecimal.ONE.add(percentFactor));
            signal.setHighPrice(newHigh);
        }
        return signal;
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
     * 设置止盈止损委托计划
     **/
    public void setPlaceTpslOrder(String symbol, String triggerPrice, String executePrice, String size, String holdSide, String planType) {
        BitgetPlaceTpslOrderParam param = new BitgetPlaceTpslOrderParam();
        param.setClientOid(IdUtil.getSnowflakeNextIdStr());
        param.setMarginCoin(DEFAULT_CURRENCY_USDT);
        param.setProductType(BG_PRODUCT_TYPE_USDT_FUTURES);
        param.setSymbol(symbol);
        /*止盈止损类型 profit_plan：止盈计划  pos_loss：仓位止损*/
        param.setPlanType(planType);
        /*触发类型 市场价格*/
        param.setTriggerType(BG_TRIGGER_TYPE_FILL_PRICE);
        param.setTriggerPrice(triggerPrice);
        param.setExecutePrice(executePrice);
        param.setSize(size);
        param.setHoldSide(holdSide);
        try {
            ResponseResult<BitgetPlaceTpslOrderResp> rs = bitgetSession.placeTpslOrder(param);
            if (rs == null || !BG_RESPONSE_CODE_SUCCESS.equals(rs.getCode())) {
                log.error("setPlaceTpslOrder: 设置止盈止损委托计划失败, param: {}", JsonUtil.toJson(param));
                return;
            }
            log.info("setPlaceTpslOrder: 设置止盈止损委托计划成功, param: {}, result: {}", JsonUtil.toJson(param), JsonUtil.toJson(rs));
        } catch (Exception e) {
            log.error("setPlaceTpslOrder-error: 设置止盈止损委托计划失败, param: {}, error: {}", JsonUtil.toJson(param), e.getMessage());
        }
    }

    /**
     * 仓位管理：判断是否允许开单 + 动态调整止损计划单
     */
    public void positionManagement() {
        try {
            // 获取当前持仓
            ResponseResult<List<BitgetAllPositionResp>> positionResp = bitgetSession.getAllPosition();
            if (positionResp == null || !BG_RESPONSE_CODE_SUCCESS.equals(positionResp.getCode())) {
                log.error("positionManagement-error: 获取仓位信息失败, rs: {}", JsonUtil.toJson(positionResp));
                return;
            }

            List<BitgetAllPositionResp> positions = Optional.ofNullable(positionResp.getData()).orElse(Collections.emptyList());
            Map<String, List<BitgetAllPositionResp>> positionMap = positions.stream().collect(Collectors.groupingBy(BitgetAllPositionResp::getSymbol));

            // 更新是否允许开单的标记
            allowOpenMap.forEach((symbol, atomicFlag) -> atomicFlag.set(!positionMap.containsKey(symbol)));

            //必须有仓位才能执行后续操作
            if (positions.isEmpty()) return;


            // 获取当前计划止盈止损委托
            ResponseResult<BitgetOrdersPlanPendingResp> planResp = bitgetSession.getOrdersPlanPending(BG_PLAN_TYPE_PROFIT_LOSS, BG_PRODUCT_TYPE_USDT_FUTURES);
            if (planResp == null || !BG_RESPONSE_CODE_SUCCESS.equals(planResp.getCode())) {
                log.error("positionManagement-error: 获取计划委托信息失败, rs: {}", JsonUtil.toJson(planResp));
                return;
            }

            List<BitgetOrdersPlanPendingResp.EntrustedOrder> entrustedOrders = Optional.ofNullable(planResp.getData()).map(BitgetOrdersPlanPendingResp::getEntrustedList).orElse(Collections.emptyList());

            for (BitgetOrdersPlanPendingResp.EntrustedOrder order : entrustedOrders) {
                //计划委托类型 pos_loss：仓位止损
                if (!BG_PLAN_TYPE_POS_LOSS.equals(order.getPlanType())) continue;
                // 检查止损计划的更新时间是否超过10分钟 , 如果未超过则不处理
                DateTime utime = DateUtil.offsetMinute(new Date(Long.parseLong(order.getUTime())), 10);
                if (DateUtil.compare(utime, new Date()) > 0) continue;

                String symbol = order.getSymbol();
                BigDecimal averagePr = dmasMap.get(symbol);
                BigDecimal lastPr = btrMap.get(symbol);
                DualMovingAverageStrategyConfig config = configMap.get(symbol);
                BitgetAllPositionResp position = positionMap.get(symbol).getFirst();

                if (averagePr == null || position == null || lastPr == null) continue;

                BigDecimal triggerPrice = new BigDecimal(order.getTriggerPrice());
                BigDecimal openPriceAvg = new BigDecimal(position.getBreakEvenPrice() != null ? position.getBreakEvenPrice() : position.getOpenPriceAvg());


                if (BG_SIDE_SELL.equals(order.getSide())) {
                    if (shouldUpdateStopLoss(averagePr, triggerPrice, openPriceAvg, lastPr, true)) {
                        // 跌 0.1%
                        BigDecimal downPrice = averagePr.multiply(BigDecimal.valueOf(0.999)).setScale(config.getPricePlace(), RoundingMode.HALF_UP);
                        modifyStopLossOrder(order, downPrice);
                    }
                } else if (BG_SIDE_BUY.equals(order.getSide())) {
                    if (shouldUpdateStopLoss(averagePr, triggerPrice, openPriceAvg, lastPr, false)) {
                        // 涨 0.1%
                        BigDecimal upPrice = averagePr.multiply(BigDecimal.valueOf(1.001)).setScale(config.getPricePlace(), RoundingMode.HALF_UP);
                        modifyStopLossOrder(order, upPrice);
                    }
                }
            }
        } catch (Exception e) {
            log.error("positionManagement-error", e);
        }
    }

    /**
     * 判断是否应更新止损价格
     */
    private boolean shouldUpdateStopLoss(BigDecimal averagePr, BigDecimal triggerPrice, BigDecimal openPrice, BigDecimal latestPrice, boolean isLong) {
        // 预定义倍数常量
        BigDecimal ratio002Plus = BigDecimal.valueOf(1.002);  // +0.2%
        BigDecimal ratio002Sub = BigDecimal.valueOf(0.998);  // -0.2%
        BigDecimal ratio1Plus = BigDecimal.valueOf(1.01);   // +1%
        BigDecimal ratio1Sub = BigDecimal.valueOf(0.99);   // -1%

        int scale = openPrice.scale();

        // 四个阈值价格
        BigDecimal openPrice002Up = openPrice.multiply(ratio002Plus).setScale(scale, RoundingMode.HALF_UP);
        BigDecimal openPrice002Dn = openPrice.multiply(ratio002Sub).setScale(scale, RoundingMode.HALF_UP);
        BigDecimal openPrice1Up = openPrice.multiply(ratio1Plus).setScale(scale, RoundingMode.HALF_UP);
        BigDecimal openPrice1Dn = openPrice.multiply(ratio1Sub).setScale(scale, RoundingMode.HALF_UP);

        boolean isIn002Range = averagePr.compareTo(openPrice002Dn) >= 0 && averagePr.compareTo(openPrice002Up) <= 0;
        boolean isOutside1Range = averagePr.compareTo(openPrice1Dn) < 0 || averagePr.compareTo(openPrice1Up) > 0;

        //均线价格不满足触发条件 (不在 ±0.2% 内，也不在 ±1% 外)
        if (!(isIn002Range || isOutside1Range)) return false;

        //多单：均线低价 > 开仓均价 且 > 当前止损价 且 最新价 > 均线低价
        if (isLong) {
            return averagePr.compareTo(openPrice) > 0 &&
                    averagePr.compareTo(triggerPrice) > 0 &&
                    latestPrice.compareTo(averagePr) > 0;

        } else {
            //空单：均线高价 < 开仓均价 且 < 当前止损价 且 最新价 < 均线高价
            return averagePr.compareTo(openPrice) < 0 &&
                    averagePr.compareTo(triggerPrice) < 0 &&
                    latestPrice.compareTo(averagePr) < 0;
        }
    }

    /**
     * 调用接口更新止损计划
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
            log.info("modifyStopLossOrder: 修改止损计划成功, param: {}, result: {}", JsonUtil.toJson(param), JsonUtil.toJson(result));
        } catch (Exception e) {
            log.error("modifyStopLossOrder-error: 更新止损计划失败, order: {}, newTriggerPrice: {}, error: {}", JsonUtil.toJson(order), newTriggerPrice, e.getMessage());
        }
    }

    /**
     * WS行情数据监控
     */
    public void marketDataWSMonitoring() {
        List<SubscribeReq> list = new ArrayList<>();
        for (DualMovingAverageStrategyConfig config : configMap.values()) {
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
                            btrMap.put(info.getSymbol(), new BigDecimal(info.getLastPr()));
                        }
                    }
                });
            } catch (Exception e) {
                log.error("marketDataWSMonitoring-error:", e);
            }
        });
    }
}
