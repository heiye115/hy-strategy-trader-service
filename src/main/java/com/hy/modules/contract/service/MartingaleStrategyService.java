package com.hy.modules.contract.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestAlgorithm;
import cn.hutool.crypto.digest.Digester;
import com.bitget.custom.entity.*;
import com.bitget.openapi.dto.response.ResponseResult;
import com.hy.common.enums.BitgetAccountType;
import com.hy.common.enums.Direction;
import com.hy.common.enums.SymbolEnum;
import com.hy.common.service.BitgetCustomService;
import com.hy.common.service.MailService;
import com.hy.common.utils.json.JsonUtil;
import com.hy.common.utils.num.AmountCalculator;
import com.hy.common.utils.num.CompoundCalculator;
import com.hy.modules.contract.entity.MartingaleOrderLevel;
import com.hy.modules.contract.entity.MartingalePlaceOrderParam;
import com.hy.modules.contract.entity.MartingaleStrategyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
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

@Slf4j
@Service
public class MartingaleStrategyService {

    private final BitgetCustomService.BitgetSession bitgetSession;

    /**
     * 邮件通知服务
     */
    private final MailService mailService;

    /**
     * 异步任务执行器
     */
    private final TaskExecutor taskExecutor;

    /**
     * Redis操作模板
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * 邮件接收地址
     */
    @Value("${spring.mail.username}")
    private String emailRecipient;


    /**
     * 订单队列 - 存储待执行的订单参数
     */
    private static final BlockingQueue<MartingalePlaceOrderParam> ORDER_QUEUE = new LinkedBlockingQueue<>(1000);

    // ==================== 控制标志 ====================

    /**
     * 订单消费者启动标志 - 确保只启动一次
     */
    private final AtomicBoolean ORDER_CONSUMER_STARTED = new AtomicBoolean(false);

    /**
     * Redis中存储马丁格尔策略配置的key
     **/
    private static final String MARTINGALE_STRATEGY_KEY = "md_conf";

    public MartingaleStrategyService(BitgetCustomService bitgetCustomService, MailService mailService, @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor, StringRedisTemplate redisTemplate) {
        this.bitgetSession = bitgetCustomService.use(BitgetAccountType.MARTINGALE);
        this.mailService = mailService;
        this.taskExecutor = taskExecutor;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 策略配置映射 - 存储各币种的交易策略参数
     */
    public final static Map<String, MartingaleStrategyConfig> STRATEGY_CONFIG_MAP = new ConcurrentHashMap<>();

    /**
     * 是否允许开单（线程安全版）
     * key: symbol, value: 是否允许开单（AtomicBoolean 保证原子性）
     **/
    private static final Map<String, AtomicBoolean> allowOpenMap = new ConcurrentHashMap<>();

    private void loadDefaultConfig() {
        // BTC配置：杠杆100倍，跌0.5%加仓，止盈2% 开启复利模式
        STRATEGY_CONFIG_MAP.put(SymbolEnum.BTCUSDT.getCode(), new MartingaleStrategyConfig(true, SymbolEnum.BTCUSDT.getCode(), Direction.LONG, 4, 1, 100, 0.5, 2.0, BigDecimal.valueOf(100.0), 20, 1.1, 1.1, "0.0001", true));
        // ETH配置：杠杆100倍，跌1%加仓，止盈2%
        STRATEGY_CONFIG_MAP.put(SymbolEnum.ETHUSDT.getCode(), new MartingaleStrategyConfig(true, SymbolEnum.ETHUSDT.getCode(), Direction.LONG, 2, 2, 100, 1.0, 2.0, BigDecimal.valueOf(100.0), 20, 1.1, 1.1, "0.01", false));

        allowOpenMap.putAll(STRATEGY_CONFIG_MAP.values().stream().collect(Collectors.toMap(MartingaleStrategyConfig::getSymbol, v -> new AtomicBoolean(false))));
    }

    /**
     * 初始化马丁策略交易服务
     * 初始化账户配置、启动订单消费者、建立WebSocket连接
     */
    public void init() {
        // 加载配置
        initializeConfig();
        // 初始化Bitget账户配置
        initializeBitgetAccount();
        // 启动订单消费者线程
        startOrderConsumer();
        log.info("马丁策略交易服务启动完成, 当前配置: {}", JsonUtil.toJson(STRATEGY_CONFIG_MAP));
    }

    /***
     * 加载配置
     **/
    public void initializeConfig() {
        try {
            List<Object> mdConfs = loadFromRedis();
            if (mdConfs.isEmpty()) {
                loadDefaultConfig();
            } else {
                updateConfig(mdConfs);
            }
        } catch (Exception e) {
            log.error("initializeConfig-error", e);
        }
    }

    private List<Object> loadFromRedis() {
        try {
            return redisTemplate.opsForHash().values(MARTINGALE_STRATEGY_KEY);
        } catch (Exception e) {
            log.error("loadFromRedis-error", e);
            return Collections.emptyList();
        }
    }


    private void updateConfig(List<Object> mdConfs) {
        Digester sha256 = new Digester(DigestAlgorithm.SHA256);
        for (Object mdConf : mdConfs) {
            MartingaleStrategyConfig newConfig = JsonUtil.toBean(String.valueOf(mdConf), MartingaleStrategyConfig.class);
            STRATEGY_CONFIG_MAP.merge(newConfig.getSymbol(), newConfig, (oldConfig, latestConfig) -> {
                if (isConfigChanged(oldConfig, latestConfig, sha256)) {
                    log.info("updateConfig: 配置更新, symbol={}, oldConfig={}, newConfig={}", newConfig.getSymbol(), JsonUtil.toJson(oldConfig), JsonUtil.toJson(latestConfig));
                    if (latestConfig.getEnable() && !oldConfig.getLeverage().equals(latestConfig.getLeverage())) {
                        setLeverageForSymbol(latestConfig);
                        log.info("updateConfig: 配置更新后，重新设置杠杆, symbol={}, leverage={}", latestConfig.getSymbol(), latestConfig.getLeverage());
                    }
                    return latestConfig;
                }
                return oldConfig;
            });
            allowOpenMap.putIfAbsent(newConfig.getSymbol(), new AtomicBoolean(false));
        }
    }

    private boolean isConfigChanged(MartingaleStrategyConfig oldConfig, MartingaleStrategyConfig newConfig, Digester sha256) {
        return !sha256.digestHex(JsonUtil.toJson(oldConfig)).equals(sha256.digestHex(JsonUtil.toJson(newConfig)));
    }

    /**
     * 初始化Bitget账户配置
     * 设置杠杆、持仓模式和保证金模式等基础交易参数
     */
    public void initializeBitgetAccount() {
        try {
            for (MartingaleStrategyConfig config : STRATEGY_CONFIG_MAP.values()) {
                if (!config.getEnable()) continue;

                // 设置杠杆倍数
                setLeverageForSymbol(config);

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
    private void setLeverageForSymbol(MartingaleStrategyConfig config) {
        try {
            ResponseResult<BitgetSetLeverageResp> rs = bitgetSession.setLeverage(
                    config.getSymbol(),
                    BG_PRODUCT_TYPE_USDT_FUTURES,
                    DEFAULT_CURRENCY_USDT,
                    config.getLeverage().toString(), null
            );
            log.info("setLeverageForSymbol-设置杠杆成功: symbol={}, leverage={}, result={}", config.getSymbol(), config.getLeverage(), JsonUtil.toJson(rs));
        } catch (Exception e) {
            log.error("setLeverageForSymbol-设置杠杆失败: symbol={}", config.getSymbol(), e);
        }
    }

    /**
     * 为指定币种设置保证金模式
     */
    private void setMarginModeForSymbol(MartingaleStrategyConfig config) {
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
     * 获取所有仓位
     **/
    public Map<String, BitgetAllPositionResp> getAllPosition() throws IOException {
        ResponseResult<List<BitgetAllPositionResp>> positionResp = bitgetSession.getAllPosition();
        List<BitgetAllPositionResp> positions = Optional.ofNullable(positionResp.getData()).orElse(Collections.emptyList());
        return positions.stream().collect(Collectors.toMap(BitgetAllPositionResp::getSymbol, p -> p, (existing, replacement) -> existing));
    }

    /**
     * 启动马丁策略
     **/
    public void startMartingaleStrategy() {
        try {
            STRATEGY_CONFIG_MAP.forEach((symbol, config) -> {
                try {
                    if (!config.getEnable()) return;
                    if (!allowOpenMap.containsKey(symbol)) return;
                    // 原子操作：如果当前是 true，才能改成 false 并继续执行
                    if (!allowOpenMap.get(symbol).compareAndSet(true, false)) {
                        // 已经是 false 了，直接跳过
                        return;
                    }
                    //批量撤单
                    cancelAllOrdersBySymbol(symbol);
                    //执行新一轮周期马丁策略开单
                    placeInitialOrder(config);
                } catch (IOException e) {
                    log.error("startMartingaleStrategy-error: symbol={}", symbol, e);
                }
            });
        } catch (Exception e) {
            log.error("startMartingaleStrategy-error: ", e);
        }
    }

    /**
     * 根据symbol批量撤单
     **/
    public void cancelAllOrdersBySymbol(String symbol) throws IOException {
        //查询当前委托
        ResponseResult<BitgetOrdersPendingResp> result = bitgetSession.getOrdersPending(symbol, BG_PRODUCT_TYPE_USDT_FUTURES);
        log.info("cancelAllOrdersBySymbol: 查询当前委托结果: symbol={}, 委托数={}", symbol, result.getData() != null && result.getData().getEntrustedList() != null ? result.getData().getEntrustedList().size() : 0);
        BitgetOrdersPendingResp data = result.getData();
        int entrustedSize = 0;
        int successSize = 0;
        if (data != null && data.getEntrustedList() != null && !data.getEntrustedList().isEmpty()) {
            List<BitgetOrdersPendingResp.EntrustedOrder> entrustedList = data.getEntrustedList();
            entrustedSize = entrustedList.size();
            //如果存在当前委托 则全部撤销委托
            BitgetBatchCancelOrdersParam param = new BitgetBatchCancelOrdersParam(symbol, BG_PRODUCT_TYPE_USDT_FUTURES, DEFAULT_CURRENCY_USDT);
            List<BitgetBatchCancelOrdersParam.Order> orderIdList = entrustedList.stream().map(o -> new BitgetBatchCancelOrdersParam.Order(o.getClientOid(), o.getOrderId())).collect(Collectors.toList());
            param.setOrderIdList(orderIdList);
            ResponseResult<BitgetBatchCancelOrdersResp> bcors = bitgetSession.batchCancelOrders(param);

            BitgetBatchCancelOrdersResp bcorsData = bcors.getData();
            if (bcorsData != null) {
                List<BitgetBatchCancelOrdersResp.Success> successList = bcorsData.getSuccessList();
                successSize = successList == null ? 0 : successList.size();
                if (successSize != entrustedSize) {
                    //撤单失败 发送邮件通知
                    String subject = "【马丁策略】撤单失败通知 - " + symbol;
                    String content = "尊敬的用户，您好！<br/><br/>在尝试启动马丁策略时，发现部分订单撤销失败。请及时登录交易所查看具体情况。<br/><br/>币种：" + symbol + "<br/>时间：" + DateUtil.formatDateTime(new Date()) + "<br/><br/>如有任何疑问，请联系技术支持。<br/><br/>祝您交易顺利！";
                    mailService.sendHtmlMail(emailRecipient, subject, content);
                    log.warn("cancelAllOrdersBySymbol: 部分订单撤销失败, symbol={}, param={}, result={}", symbol, JsonUtil.toJson(param), JsonUtil.toJson(bcors));
                }
            }
        }
        log.info("cancelAllOrdersBySymbol: 批量撤单结果:{} symbol={}, 当前委托数量={}, 撤单成功数量={}", successSize == entrustedSize ? "成功" : "失败", symbol, entrustedSize, successSize);
    }

    /**
     * 生成初始订单并加入队列
     **/
    public void placeInitialOrder(MartingaleStrategyConfig config) {
        MartingalePlaceOrderParam order = new MartingalePlaceOrderParam();
        order.setClientOid(IdUtil.getSnowflakeNextIdStr());
        order.setSymbol(config.getSymbol());
        order.setOrderType(BG_ORDER_TYPE_MARKET);
        order.setMarginMode(BG_MARGIN_MODE_CROSSED);
        order.setSize(config.getMinTradeSize());
        order.setSide(config.getDirection() == Direction.LONG ? BG_SIDE_BUY : BG_SIDE_SELL);
        if (ORDER_QUEUE.offer(order)) {
            log.info("placeInitialOrder: 队列添加订单成功, order: {}", JsonUtil.toJson(order));
        }
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
                        MartingalePlaceOrderParam orderParam = ORDER_QUEUE.take(); // 阻塞直到有数据

                        // 校验当前是否已有仓位
                        if (getAllPosition().containsKey(orderParam.getSymbol())) continue;

                        // 校验账户余额
                        if (!validateAccountBalance(orderParam)) continue;


                        log.info("startOrderConsumer: 准备下单，订单:{}  ", JsonUtil.toJson(orderParam));

                        // 执行下单
                        ResponseResult<BitgetPlaceOrderResp> orderResult = executeOrder(orderParam);
                        if (orderResult.getData() == null) {
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
     * 处理下单成功后的操作
     */
    private void handleSuccessfulOrder(MartingalePlaceOrderParam orderParam, BitgetPlaceOrderResp orderResult) {
        try {
            MartingaleStrategyConfig config = STRATEGY_CONFIG_MAP.get(orderParam.getSymbol());

            //设置仓位止盈止损
            setPositionTpsl(config);

            ResponseResult<BitgetOrderDetailResp> orderDetailResult = bitgetSession.getOrderDetail(orderParam.getSymbol(), orderResult.getOrderId());
            if (orderDetailResult.getData() == null) {
                log.error("handleSuccessfulOrder:获取订单详情失败，orderParam:{} orderResult: {}, 错误信息: {}", JsonUtil.toJson(orderParam), JsonUtil.toJson(orderResult), JsonUtil.toJson(orderDetailResult));
                return;
            }

            BitgetOrderDetailResp orderDetail = orderDetailResult.getData();

            BigDecimal entryPrice = new BigDecimal(orderDetail.getPriceAvg()); // 初始下单价
            BigDecimal baseStep = BigDecimal.valueOf(config.getAddPositionPercentThreshold()).divide(new BigDecimal(100), 3, RoundingMode.HALF_UP);         // 1%
            BigDecimal amountMultiplier = BigDecimal.valueOf(config.getAddPositionAmountMultiple());  // 加仓金额倍数
            BigDecimal stepMultiplier = BigDecimal.valueOf(config.getAddPositionPriceMultiple());    // 加仓价差倍数
            BigDecimal leverage = BigDecimal.valueOf(config.getLeverage());            // 杠杆倍数
            BigDecimal maxTotalMargin = config.getMaxInvestAmount(); // 最大投入保证金
            if (config.getCompoundEnable()) {
                CompoundCalculator.CompoundRow plan = CompoundCalculator.getCompoundPlan(orderParam.getAccountBalance());
                if (gt(plan.getPosition(), maxTotalMargin)) {
                    maxTotalMargin = plan.getPosition();
                    log.info("handleSuccessfulOrder: 复利模式下，最大投入保证金:{} 复利计划={}", maxTotalMargin.toPlainString(), JsonUtil.toJson(plan));
                }
            }
            int maxAddCount = config.getMaxOpenTimes();
            Direction direction = config.getDirection();
            Integer pricePlace = config.getPricePlace();
            Integer volumePlace = config.getVolumePlace();

            List<MartingaleOrderLevel> plan = generateOrderPlanMaxMargin(
                    entryPrice, baseStep, maxAddCount, amountMultiplier,
                    stepMultiplier, leverage, maxTotalMargin, direction,
                    pricePlace, volumePlace
            );

            List<BitgetBatchPlaceOrderParam.Order> orderList = plan.stream().map(orderLevel -> {
                BitgetBatchPlaceOrderParam.Order order = new BitgetBatchPlaceOrderParam.Order();
                order.setClientOid(IdUtil.getSnowflakeNextIdStr());
                order.setSize(orderLevel.getVolume().toPlainString());
                order.setPrice(orderLevel.getPrice().toPlainString());
                order.setSide(direction == Direction.LONG ? BG_SIDE_BUY : BG_SIDE_SELL);
                order.setOrderType(BG_ORDER_TYPE_LIMIT);
                order.setForce(BG_FORCE_GTC);
                return order;
            }).collect(Collectors.toList());

            //初始订单组装下单参数
            BitgetBatchPlaceOrderParam param = new BitgetBatchPlaceOrderParam();
            param.setSymbol(orderParam.getSymbol());
            param.setProductType(BG_PRODUCT_TYPE_USDT_FUTURES);
            param.setMarginCoin(DEFAULT_CURRENCY_USDT);
            param.setMarginMode(BG_MARGIN_MODE_CROSSED);
            param.setOrderList(orderList);
            ResponseResult<BitgetBatchPlaceOrderResp> result = bitgetSession.batchPlaceOrder(param);
            log.info("handleSuccessfulOrder: 批量下单结果: orderParam={}, orderResult={}, param={}, result={}", JsonUtil.toJson(orderParam), JsonUtil.toJson(orderResult), JsonUtil.toJson(param), JsonUtil.toJson(result));
            //批量下单失败 发送邮件通知
            if (orderList.size() != result.getData().getSuccessList().size()) {
                String subject = "【马丁策略】批量下单失败通知 - " + orderParam.getSymbol();
                String content = "尊敬的用户，您好！<br/><br/>在尝试启动马丁策略时，发现部分订单未能成功创建。请及时登录交易所查看具体情况。<br/><br/>币种：" + orderParam.getSymbol() + "<br/>时间：" + DateUtil.formatDateTime(new Date()) + "<br/><br/>如有任何疑问，请联系技术支持。<br/><br/>祝您交易顺利！";
                mailService.sendHtmlMail(emailRecipient, subject, content);
                log.warn("handleSuccessfulOrder: 部分订单创建失败, orderParam={}, orderResult={}, param={}, result={}", JsonUtil.toJson(orderParam), JsonUtil.toJson(orderResult), JsonUtil.toJson(param), JsonUtil.toJson(result));
            }
        } catch (Exception e) {
            log.error("handleSuccessfulOrder-error: orderParam={}, orderResult={}", JsonUtil.toJson(orderParam), JsonUtil.toJson(orderResult), e);
        }
    }

    /**
     * 设置仓位止盈止损
     **/
    public void setPositionTpsl(MartingaleStrategyConfig config) {
        String symbol = config.getSymbol();
        try {
            ResponseResult<List<BitgetAllPositionResp>> singlePosition = bitgetSession.getSinglePosition(symbol);
            List<BitgetAllPositionResp> datas = singlePosition.getData();
            if (datas == null || datas.isEmpty()) {
                log.warn("setPositionTpsl: 未获取到持仓信息，无法设置止盈止损! symbol: {}", symbol);
                return;
            }
            BitgetAllPositionResp position = datas.stream().filter(p -> config.getDirection().name().toLowerCase().equals(p.getHoldSide())).findFirst().orElse(null);
            if (position == null) return;
            //仓位盈亏平衡价
            BigDecimal breakEvenPrice = new BigDecimal(position.getBreakEvenPrice()).setScale(config.getPricePlace(), RoundingMode.HALF_UP);
            //仓位止盈价
            BigDecimal takeProfitPrice;
            //仓位止损价
            BigDecimal stopLossPrice;
            //初始止损默认50%
            BigDecimal stopLossPercent = BigDecimal.valueOf(50);
            boolean isLong = Direction.LONG == config.getDirection();
            String side = isLong ? BG_SIDE_BUY : BG_SIDE_SELL;

            if (isLong) {
                takeProfitPrice = AmountCalculator.increase(breakEvenPrice, BigDecimal.valueOf(config.getTakeProfitPercentThreshold()), config.getPricePlace());
                stopLossPrice = AmountCalculator.decrease(breakEvenPrice, stopLossPercent, config.getPricePlace());
            } else {
                takeProfitPrice = AmountCalculator.decrease(breakEvenPrice, BigDecimal.valueOf(config.getTakeProfitPercentThreshold()), config.getPricePlace());
                stopLossPrice = AmountCalculator.increase(breakEvenPrice, stopLossPercent, config.getPricePlace());
            }

            // 设置仓位止盈
            setPlaceTpslOrder(symbol, takeProfitPrice, takeProfitPrice, null, side, BG_PLAN_TYPE_POS_PROFIT);

            // 设置仓位止损
            setPlaceTpslOrder(symbol, stopLossPrice, null, null, side, BG_PLAN_TYPE_POS_LOSS);
        } catch (Exception e) {
            log.error("setPositionTpsl-error: symbol={}", symbol, e);
        }
    }


    /**
     * 验证账户余额
     */
    private boolean validateAccountBalance(MartingalePlaceOrderParam orderParam) {
        Map<String, BitgetAccountsResp> accountMap = getAccountInfo();
        BitgetAccountsResp accountsResp = accountMap.get(DEFAULT_CURRENCY_USDT);
        if (accountsResp == null) {
            log.warn("validateAccountBalance: 未获取到USDT账户信息，无法执行下单! 订单: {}", JsonUtil.toJson(orderParam));
            return false;
        }

        MartingaleStrategyConfig config = STRATEGY_CONFIG_MAP.get(orderParam.getSymbol());
        BigDecimal available = new BigDecimal(accountsResp.getAvailable());
        BigDecimal crossedMaxAvailable = new BigDecimal(accountsResp.getCrossedMaxAvailable());
        BigDecimal maxInvestAmount = config.getMaxInvestAmount();
        orderParam.setAccountBalance(available);

        if (lt(available, maxInvestAmount) || lt(crossedMaxAvailable, maxInvestAmount)) {
            log.warn("validateAccountBalance: USDT账户可用余额不足，无法执行下单操作! 订单: {} 可用余额: {}, 全仓最大可用来开仓余额: {}", JsonUtil.toJson(orderParam), available, crossedMaxAvailable);
            return false;
        }
        return true;
    }


    /**
     * 执行下单操作
     */
    private ResponseResult<BitgetPlaceOrderResp> executeOrder(MartingalePlaceOrderParam orderParam) throws Exception {
        return bitgetSession.placeOrder(
                orderParam.getClientOid(),
                orderParam.getSymbol(),
                orderParam.getSize(),
                orderParam.getSide(),
                null,
                orderParam.getOrderType(),
                orderParam.getMarginMode()
        );
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
     * 设置止盈止损计划委托下单
     */
    public void setPlaceTpslOrder(String symbol, BigDecimal triggerPrice, BigDecimal executePrice, BigDecimal size, String holdSide, String planType) {
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
     * 仓位管理
     */
    public void managePositions() {
        try {
            // 获取当前所有持仓
            Map<String, BitgetAllPositionResp> positionMap = getAllPosition();

            // 更新是否允许开单的标记
            allowOpenMap.forEach((symbol, atomicFlag) -> atomicFlag.set(!positionMap.containsKey(symbol)));

            // 必须有仓位才能执行后续操作
            if (positionMap.isEmpty()) return;

            // 获取当前计划止盈止损委托
            Map<String, List<BitgetOrdersPlanPendingResp.EntrustedOrder>> entrustedOrdersMap = getOrdersPlanPending();

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
        if (positionMap.isEmpty() || entrustedOrdersMap.isEmpty()) return;
        try {
            positionMap.forEach((symbol, position) -> {
                MartingaleStrategyConfig config = STRATEGY_CONFIG_MAP.get(symbol);
                BigDecimal total = new BigDecimal(position.getTotal());
                BigDecimal breakEvenPrice = new BigDecimal(position.getBreakEvenPrice()).setScale(config.getPricePlace(), RoundingMode.HALF_UP);
                BigDecimal maxInvestAmount = config.getMaxInvestAmount();

                List<BitgetOrdersPlanPendingResp.EntrustedOrder> entrustedOrders = entrustedOrdersMap.get(symbol);
                if (entrustedOrders == null || entrustedOrders.isEmpty()) return;

                for (BitgetOrdersPlanPendingResp.EntrustedOrder order : entrustedOrders) {
                    BigDecimal triggerPrice = new BigDecimal(order.getTriggerPrice());
                    String planType = order.getPlanType();
                    String side = order.getSide();
                    //仓位止损
                    if (BG_PLAN_TYPE_POS_LOSS.equals(planType)) {
                        //做多 sell 卖
                        if (BG_SIDE_SELL.equals(side)) {
                            //计算止损触发价格
                            BigDecimal newTriggerPrice = calculateStopLossPrice(breakEvenPrice, total, maxInvestAmount, Direction.LONG, config.getPricePlace());
                            if (ne(triggerPrice, newTriggerPrice) && gt(newTriggerPrice, BigDecimal.ZERO)) {
                                modifyStopLossOrder(order, newTriggerPrice, null);
                            }
                        }
                        //做空 buy 买
                        else if (BG_SIDE_BUY.equals(side)) {
                            BigDecimal newTriggerPrice = calculateStopLossPrice(breakEvenPrice, total, maxInvestAmount, Direction.SHORT, config.getPricePlace());
                            if (ne(triggerPrice, newTriggerPrice)) {
                                modifyStopLossOrder(order, newTriggerPrice, null);
                            }
                        }
                    }
                    //仓位止盈
                    else if (BG_PLAN_TYPE_POS_PROFIT.equals(planType)) {
                        //做多 sell 卖
                        if (BG_SIDE_SELL.equals(side)) {
                            BigDecimal newTriggerPrice = AmountCalculator.increase(breakEvenPrice, BigDecimal.valueOf(config.getTakeProfitPercentThreshold()), config.getPricePlace());
                            if (ne(triggerPrice, newTriggerPrice)) {
                                modifyStopLossOrder(order, newTriggerPrice, newTriggerPrice);
                            }
                        }
                        //做空 buy 买
                        else if (BG_SIDE_BUY.equals(side)) {
                            BigDecimal newTriggerPrice = AmountCalculator.decrease(breakEvenPrice, BigDecimal.valueOf(config.getTakeProfitPercentThreshold()), config.getPricePlace());
                            if (ne(triggerPrice, newTriggerPrice)) {
                                modifyStopLossOrder(order, newTriggerPrice, newTriggerPrice);
                            }
                        }
                    }
                }
            });
        } catch (Exception e) {
            log.error("updateTpslPlans-error", e);
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
     * 生成马丁格尔每单价格和保证金序列（最大投入金额分配版）
     *
     * @param entryPrice       初始下单价
     * @param baseStep         每次跌/涨多少加仓（百分比，例如 1% = 0.01）
     * @param maxAddCount      最大加仓次数
     * @param amountMultiplier 加仓金额倍数
     * @param stepMultiplier   加仓价差倍数
     * @param leverage         杠杆倍数
     * @param maxTotalMargin   最大投入保证金
     * @param direction        多单 LONG / 空单 SHORT
     * @return 每单信息列表
     */
    public static List<MartingaleOrderLevel> generateOrderPlanMaxMargin(
            BigDecimal entryPrice, BigDecimal baseStep,
            int maxAddCount, BigDecimal amountMultiplier,
            BigDecimal stepMultiplier, BigDecimal leverage,
            BigDecimal maxTotalMargin, Direction direction,
            Integer pricePlace, Integer volumePlace
    ) {
        List<MartingaleOrderLevel> levels = new ArrayList<>();
        BigDecimal cumulativeStep = BigDecimal.ZERO;

        // 先计算未缩放的每单保证金（按倍数递增）
        List<BigDecimal> rawMargins = new ArrayList<>();
        BigDecimal totalRaw = BigDecimal.ZERO;
        for (int i = 0; i < maxAddCount; i++) {
            BigDecimal raw = amountMultiplier.pow(i);
            rawMargins.add(raw);
            totalRaw = totalRaw.add(raw);
        }

        // 计算缩放系数，使总投入不超过 maxTotalMargin
        BigDecimal scale = maxTotalMargin.divide(totalRaw, 16, RoundingMode.HALF_UP);

        for (int i = 0; i < maxAddCount; i++) {
            // 本次加仓保证金 = rawMargin * scale * leverage
            BigDecimal margin = rawMargins.get(i).multiply(scale).multiply(leverage);

            // 当前价差增量 = baseStep * (stepMultiplier ^ i)
            BigDecimal stepIncrement = baseStep.multiply(stepMultiplier.pow(i));

            // 累计下跌/上涨百分比
            cumulativeStep = cumulativeStep.add(stepIncrement);

            // 触发价格
            BigDecimal price;
            if (direction == Direction.LONG) {
                price = entryPrice.multiply(BigDecimal.ONE.subtract(cumulativeStep));
            } else {
                price = entryPrice.multiply(BigDecimal.ONE.add(cumulativeStep));
            }

            BigDecimal newCumulativeStep = cumulativeStep.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal newPrice = price.setScale(pricePlace, RoundingMode.HALF_UP);
            BigDecimal newMargin = margin.setScale(3, RoundingMode.HALF_UP);
            BigDecimal volume = newMargin.divide(newPrice, volumePlace, RoundingMode.HALF_UP);
            levels.add(new MartingaleOrderLevel(i + 1, newPrice, newMargin, volume, newCumulativeStep));
        }
        return levels;
    }


    /**
     * 计算止损触发价格
     *
     * @param avgEntryPrice 当前持仓均价（盈亏平衡价）
     * @param positionQty   当前持仓数量（正数）
     * @param targetLoss    最大允许亏损金额（正数）
     * @param direction     交易方向（做多/做空）
     * @return 触发止损的目标价格
     */
    public static BigDecimal calculateStopLossPrice(BigDecimal avgEntryPrice, BigDecimal positionQty, BigDecimal targetLoss, Direction direction, int newScale) {
        if (positionQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("持仓数量必须大于0");
        }
        if (targetLoss.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("目标亏损必须为正数");
        }
        // 每单位的亏损额度
        BigDecimal perUnitLoss = targetLoss.divide(positionQty, 10, RoundingMode.HALF_UP);
        BigDecimal stopLossPrice;
        if (direction == Direction.LONG) {
            // 做多：价格下跌会亏损
            stopLossPrice = avgEntryPrice.subtract(perUnitLoss);
        } else {
            // 做空：价格上涨会亏损
            stopLossPrice = avgEntryPrice.add(perUnitLoss);
        }
        return stopLossPrice.setScale(newScale, RoundingMode.HALF_UP);
    }

    /**
     * 修改止盈止损计划
     */
    private void modifyStopLossOrder(BitgetOrdersPlanPendingResp.EntrustedOrder order, BigDecimal newTriggerPrice, BigDecimal newExecutePrice) {
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
            param.setSize("");
            ResponseResult<BitgetPlaceTpslOrderResp> result = bitgetSession.modifyTpslOrder(param);
            log.info("modifyStopLossOrder: 修改止盈止损计划成功, param: {}, result: {}", JsonUtil.toJson(param), JsonUtil.toJson(result));
        } catch (Exception e) {
            log.error("modifyStopLossOrder-error: 更新止盈止损计划失败, order: {}, newTriggerPrice: {}, error: {}", JsonUtil.toJson(order), newTriggerPrice, e.getMessage());
        }
    }

}
