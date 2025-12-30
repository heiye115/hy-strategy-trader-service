package com.hy.modules.contract.task;

import com.hy.modules.contract.service.DoubleMovingAverageStrategyV2Service;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "task.doublemovingaverage", name = "enabled", havingValue = "true")
public class DoubleMovingAverageStrategyTaskV2Service {

    private final DoubleMovingAverageStrategyV2Service doubleMovingAverageStrategyV2Service;

    public DoubleMovingAverageStrategyTaskV2Service(DoubleMovingAverageStrategyV2Service doubleMovingAverageStrategyService) {
        this.doubleMovingAverageStrategyV2Service = doubleMovingAverageStrategyService;
    }

    /**
     * 初始化方法双均线策略
     */
    @PostConstruct
    public void init() {
        //启动时执行一次
        updateDoubleMovingAverageIndicators();
        doubleMovingAverageStrategyV2Service.init();
    }

    /**
     * 更新双均线指标数据
     * 每五分钟执行一次
     **/
    @Scheduled(cron = "0 */5 * * * ?")
    public void updateDoubleMovingAverageIndicators() {
        try {
            doubleMovingAverageStrategyV2Service.updateDoubleMovingAverageIndicators();
        } catch (Exception e) {
            log.error("updateDoubleMovingAverageIndicators-error", e);
        }
    }

    /**
     * 刷新市场价格缓存
     * 每60秒执行一次
     **/
    @Scheduled(fixedRate = 60000)
    public void refreshMarketPriceCache() {
        try {
            doubleMovingAverageStrategyV2Service.refreshMarketPriceCache();
        } catch (Exception e) {
            log.error("refreshMarketPriceCache-error", e);
        }
    }

    /**
     * 检测交易信号并入队
     * 每500毫秒执行一次
     * 即使上次没执行完，调度器也会按时间启动新任务（可能重叠）
     **/
    @Scheduled(fixedRate = 500)
    public void detectAndEnqueueTradingSignals() {
        try {
            doubleMovingAverageStrategyV2Service.detectAndEnqueueTradingSignals();
        } catch (Exception e) {
            log.error("detectAndEnqueueTradingSignals-error", e);
        }
    }

    /**
     * 仓位管理
     * 每秒执行一次
     **/
    @Scheduled(fixedDelay = 1000)
    public void managePositions() {
        try {
            doubleMovingAverageStrategyV2Service.managePositions();
        } catch (Exception e) {
            log.error("managePositions-error", e);
        }
    }
}
