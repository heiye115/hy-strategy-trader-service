package com.hy.modules.contract.service;

import cn.hutool.core.util.IdUtil;
import com.bitget.custom.entity.*;
import com.bitget.openapi.dto.response.ResponseResult;
import com.hy.common.constants.BitgetConstant;
import com.hy.common.enums.SymbolEnum;
import com.hy.common.service.BitgetCustomService;
import com.hy.common.service.MailService;
import com.hy.common.utils.json.JsonUtil;
import com.hy.modules.contract.entity.MartingaleStrategyConfig;
import com.hy.modules.contract.entity.RangePricePlaceOrderParam;
import com.hy.modules.contract.entity.RangePriceStrategyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.hy.common.constants.BitgetConstant.*;
import static com.hy.common.utils.num.BigDecimalUtils.lt;

@Slf4j
@Service
public class MartingaleStrategyService {

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

    /**
     * 邮件接收地址
     */
    @Value("${spring.mail.username}")
    private String emailRecipient;


    /**
     * 订单队列 - 存储待执行的订单参数
     */
    private static final BlockingQueue<RangePricePlaceOrderParam> ORDER_QUEUE = new LinkedBlockingQueue<>(1000);

    // ==================== 控制标志 ====================

    /**
     * 订单消费者启动标志 - 确保只启动一次
     */
    private final AtomicBoolean ORDER_CONSUMER_STARTED = new AtomicBoolean(false);

    public MartingaleStrategyService(BitgetCustomService bitgetCustomService, MailService mailService, @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.bitgetCustomService = bitgetCustomService;
        this.bitgetSession = bitgetCustomService.use(BitgetConstant.MARTINGALE_ACCOUNT);
        this.mailService = mailService;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 策略配置映射 - 存储各币种的交易策略参数
     */
    public final static Map<String, MartingaleStrategyConfig> STRATEGY_CONFIG_MAP = new ConcurrentHashMap<>() {
        {
            // BTC配置：杠杆100倍，跌1%加仓，止盈2%
            put(SymbolEnum.BTCUSDT.getCode(), new MartingaleStrategyConfig(true, SymbolEnum.BTCUSDT.getCode(), BitgetConstant.BG_SIDE_BUY, 4, 1, 100, 1.0, 2.0, BigDecimal.valueOf(1.0), 20, 1.1, 1.1));
        }
    };


    /**
     * 启动马丁策略交易服务
     * 初始化账户配置、启动订单消费者、建立WebSocket连接
     */
    public void start() {
        // 初始化Bitget账户配置
        initializeBitgetAccount();
        // 启动订单消费者线程
        startOrderConsumer();
        log.info("马丁策略交易服务启动完成, 当前配置: {}", JsonUtil.toJson(STRATEGY_CONFIG_MAP));
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
                setLeverageForSymbol(config.getSymbol(), config.getLeverage());

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
     * 启动马丁策略
     **/
    public void startMartingaleStrategy() {
        // 启动马丁策略逻辑
        log.info("startMartingaleStrategy-启动马丁策略");
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


                        // 计算开仓参数
                        //calculateOrderParameters(orderParam, leverage);
                        //log.info("startOrderConsumer: 准备下单，订单:{} 区间价格信息:{}", JsonUtil.toJson(orderParam), JsonUtil.toJson(RANGE_PRICE_CACHE.get(orderParam.getSymbol())));

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
     * 检查是否已有仓位
     */
    private boolean hasExistingPosition(String symbol) throws Exception {
        List<BitgetAllPositionResp> positions = Optional.ofNullable(bitgetSession.getAllPosition().getData()).orElse(Collections.emptyList());
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

        MartingaleStrategyConfig config = STRATEGY_CONFIG_MAP.get(orderParam.getSymbol());
        BigDecimal available = new BigDecimal(accountsResp.getAvailable());
        BigDecimal crossedMaxAvailable = new BigDecimal(accountsResp.getCrossedMaxAvailable());
        BigDecimal openAmount = null;//config.getOpenAmount();

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
//        MartingaleStrategyConfig config = STRATEGY_CONFIG_MAP.get(orderParam.getSymbol());
//        Map<String, BitgetAccountsResp> accountMap = getAccountInfo();
//        BitgetAccountsResp accountsResp = accountMap.get(DEFAULT_CURRENCY_USDT);
//
//        BigDecimal available = new BigDecimal(accountsResp.getAvailable());
//        BigDecimal crossedMaxAvailable = new BigDecimal(accountsResp.getCrossedMaxAvailable());
//        BigDecimal openAmount = config.getOpenAmount();
//
//        // 计算开仓金额（取初始值或比例值）
//        BigDecimal proportionAmount = available.multiply(OPEN_POSITION_RATIO).setScale(2, RoundingMode.HALF_UP);
//        if (gt(proportionAmount, openAmount) && gte(crossedMaxAvailable, proportionAmount)) {
//            openAmount = proportionAmount;
//        }
//
//        // 计算实际开仓数量
//        BigDecimal realityOpenAmount = openAmount.multiply(BigDecimal.valueOf(leverage));
//        BigDecimal size = realityOpenAmount.divide(orderParam.getPrice(), config.getVolumePlace(), RoundingMode.HALF_UP);
//        orderParam.setSize(size.toPlainString());
    }

    /**
     * 执行下单操作
     */
    private ResponseResult<BitgetPlaceOrderResp> executeOrder(RangePricePlaceOrderParam orderParam) throws Exception {
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
    private void handleSuccessfulOrder(RangePricePlaceOrderParam orderParam, BitgetPlaceOrderResp orderResult) {
//        RangePriceOrder order = BeanUtil.toBean(orderParam, RangePriceOrder.class);
//        order.setOrderId(orderResult.getOrderId());
//        order.setClientOid(orderResult.getClientOid());
//
//        log.info("handleSuccessfulOrder: 下单成功，订单信息:{} , Bitget订单信息:{}", JsonUtil.toJson(orderParam), JsonUtil.toJson(order));
//
//        // 设置延迟开单时间
//        DELAY_OPEN_TIME_MAP.put(orderParam.getSymbol(), System.currentTimeMillis() + DELAY_OPEN_TIME_MS);
//
//        // 设置止损
//        setStopLossOrder(orderParam.getSymbol(), orderParam.getPresetStopLossPrice(), null, null, orderParam.getSide(), BG_PLAN_TYPE_POS_LOSS);
//
//        // 设置分批止盈
//        RangePriceStrategyConfig config = STRATEGY_CONFIG_MAP.get(orderParam.getSymbol());
//        setBatchTakeProfitOrders(orderResult.getOrderId(), orderParam, config);
    }

    /**
     * 设置分批止盈订单
     */
    public void setBatchTakeProfitOrders(String orderId, RangePricePlaceOrderParam orderParam, RangePriceStrategyConfig config) {
        try {
            String symbol = orderParam.getSymbol();
            ResponseResult<BitgetOrderDetailResp> orderDetailResult = bitgetSession.getOrderDetail(symbol, orderId);
            if (!BG_RESPONSE_CODE_SUCCESS.equals(orderDetailResult.getCode()) || orderDetailResult.getData() == null) {
                log.error("setBatchTakeProfitOrders:获取订单详情失败，订单ID: {}, 错误信息: {}", orderId, JsonUtil.toJson(orderDetailResult));
                return;
            }
            BitgetOrderDetailResp orderDetail = orderDetailResult.getData();
            BigDecimal totalVolume = new BigDecimal(orderDetail.getBaseVolume());
            BigDecimal priceAvg = new BigDecimal(orderDetail.getPriceAvg());
            BigDecimal presetStopSurplusPrice1 = BigDecimal.ZERO;
            BigDecimal presetStopSurplusPrice2 = orderParam.getPresetStopSurplusPrice2();
            BigDecimal presetStopSurplusPrice3 = orderParam.getPresetStopSurplusPrice3();
            BigDecimal presetStopLossPrice = orderParam.getPresetStopLossPrice();
            String side = orderParam.getSide();
            Integer pricePlace = config.getPricePlace();
            Integer volumePlace = config.getVolumePlace();
            Double takeProfitPositionPercent1 = config.getTakeProfitPositionPercent1();
            Double takeProfitPositionPercent2 = config.getTakeProfitPositionPercent2();

            //计算预设止盈价1
            if (BG_SIDE_BUY.equals(side)) {
                presetStopSurplusPrice1 = priceAvg.add(priceAvg.subtract(presetStopLossPrice)).setScale(pricePlace, RoundingMode.HALF_UP);
            } else if (BG_SIDE_SELL.equals(side)) {
                presetStopSurplusPrice1 = priceAvg.subtract(presetStopLossPrice.subtract(priceAvg)).setScale(pricePlace, RoundingMode.HALF_UP);
            }

            // 设置仓位止盈
            setStopLossOrder(symbol, presetStopSurplusPrice3, presetStopSurplusPrice3, null, side, BG_PLAN_TYPE_POS_PROFIT);

            // 设置分批止盈计划
            BigDecimal takeProfitPosition2 = totalVolume.multiply(BigDecimal.valueOf(takeProfitPositionPercent2 / 100.0)).setScale(volumePlace, RoundingMode.HALF_UP);
            setStopLossOrder(symbol, presetStopSurplusPrice2, presetStopSurplusPrice2, takeProfitPosition2, side, BG_PLAN_TYPE_PROFIT_PLAN);

            BigDecimal takeProfitPosition1 = totalVolume.multiply(BigDecimal.valueOf(takeProfitPositionPercent1 / 100.0)).setScale(volumePlace, RoundingMode.HALF_UP);
            setStopLossOrder(symbol, presetStopSurplusPrice1, presetStopSurplusPrice1, takeProfitPosition1, side, BG_PLAN_TYPE_PROFIT_PLAN);

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
            //ResponseResult<BitgetPlaceTpslOrderResp> result = bitgetCustomService.modifyTpslOrder(param);
            //log.info("modifyStopLossOrder: 修改止盈止损计划成功, param: {}, result: {}", JsonUtil.toJson(param), JsonUtil.toJson(result));
        } catch (Exception e) {
            log.error("modifyStopLossOrder-error: 更新止盈止损计划失败, order: {}, newTriggerPrice: {}, error: {}", JsonUtil.toJson(order), newTriggerPrice, e.getMessage());
        }
    }

    /**
     * 启动WebSocket行情数据监控
     */
//    public void startWebSocketMarketDataMonitoring() {
//        List<SubscribeReq> subscribeRequests = new ArrayList<>();
//        for (RangePriceStrategyConfig config : STRATEGY_CONFIG_MAP.values()) {
//            subscribeRequests.add(SubscribeReq.builder()
//                    .instType(BG_PRODUCT_TYPE_USDT_FUTURES)
//                    .channel(BG_CHANNEL_TICKER)
//                    .instId(config.getSymbol())
//                    .build());
//        }
//
//        if (subscribeRequests.isEmpty()) return;
//
//        taskExecutor.execute(() -> {
//            try {
//                bitgetCustomService.subscribeWsClientContractPublic(subscribeRequests, data -> {
//                    if (data != null) {
//                        BitgetWSMarketResp marketResp = JsonUtil.toBean(data, BitgetWSMarketResp.class);
//                        if (marketResp.getData() != null && !marketResp.getData().isEmpty()) {
//                            BitgetWSMarketResp.MarketInfo info = marketResp.getData().getFirst();
//                            MARKET_PRICE_CACHE.put(info.getSymbol(), new BigDecimal(info.getLastPr()));
//                        }
//                    }
//                });
//            } catch (Exception e) {
//                log.error("startWebSocketMarketDataMonitoring-error:", e);
//            }
//        });
//    }

    /**
     * 发送区间价格信息邮件
     * 定时发送HTML格式的区间价格报告
     */
    public void sendRangePriceEmail() {
//        if (RANGE_PRICE_CACHE.isEmpty()) return;
//
        try {
//            StringBuilder content = new StringBuilder();
//            content.append("<html><body>");
//            content.append("<h2>📊 ").append(DateUtil.formatDateTime(new Date())).append("区间价格信息报告</h2>");
//            content.append("<table border='1' cellpadding='8' cellspacing='0' style='border-collapse:collapse;'>");
//            content.append("<thead><tr>")
//                    .append("<th>币种</th>")
//                    .append("<th>最高均价</th>")
//                    .append("<th>最低均价</th>")
//                    .append("<th>最高价</th>")
//                    .append("<th>最高价时间</th>")
//                    .append("<th>均价</th>")
//                    .append("<th>最低价</th>")
//                    .append("<th>最低价时间</th>")
//                    .append("<th>区间数</th>")
//                    .append("</tr></thead>");
//            content.append("<tbody>");
//
//            for (RangePrice rangePrice : RANGE_PRICE_CACHE.values()) {
//                content.append("<tr>")
//                        .append("<td>").append(rangePrice.getSymbol()).append("</td>")
//                        .append("<td>").append(rangePrice.getHighAveragePrice()).append("</td>")
//                        .append("<td>").append(rangePrice.getLowAveragePrice()).append("</td>")
//                        .append("<td>").append(rangePrice.getHighPrice()).append("</td>")
//                        .append("<td>").append(DateUtil.formatDateTime(new Date(rangePrice.getHighPriceTimestamp()))).append("</td>")
//                        .append("<td>").append(rangePrice.getAveragePrice()).append("</td>")
//                        .append("<td>").append(rangePrice.getLowPrice()).append("</td>")
//                        .append("<td>").append(DateUtil.formatDateTime(new Date(rangePrice.getLowPriceTimestamp()))).append("</td>")
//                        .append("<td>").append(rangePrice.getRangeCount()).append("</td>")
//                        .append("</tr>");
//            }
//            content.append("</tbody></table>");
//            content.append("<p style='color:gray;font-size:12px;'>此邮件为系统自动发送，请勿回复。</p>");
//            content.append("</body></html>");

            // 发送HTML邮件
            //mailService.sendHtmlMail(emailRecipient, DateUtil.now() + " 区间价格信息", content.toString());
        } catch (Exception e) {
            log.error("sendRangePriceEmail-error:", e);
        }
    }

}
