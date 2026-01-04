package com.hy.modules.schedule;

import com.hy.modules.dex.service.MovingAverageStrategyService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "task.movingaverage", name = "enabled", havingValue = "true")
public class MovingAverageStrategyTaskService {

    private final MovingAverageStrategyService movingAverageStrategyService;

    public MovingAverageStrategyTaskService(MovingAverageStrategyService movingAverageStrategyService) {
        this.movingAverageStrategyService = movingAverageStrategyService;
    }

    /**
     * 初始化方法双均线策略
     */
    @PostConstruct
    public void init() {
        //启动时执行一次
        updateMovingAverageIndicators();
        movingAverageStrategyService.init();
    }

    /**
     * 更新双均线指标数据
     * 每五分钟执行一次
     **/
    @Scheduled(cron = "0 */5 * * * ?")
    public void updateMovingAverageIndicators() {
        try {
            movingAverageStrategyService.updateMovingAverageIndicators();
        } catch (Exception e) {
            log.error("updateMovingAverageIndicators-error", e);
        }
    }

    /**
     * 刷新市场价格缓存
     * 每60秒执行一次
     **/
    @Scheduled(fixedRate = 60000)
    public void refreshMarketPriceCache() {
        try {
            movingAverageStrategyService.refreshMarketPriceCache();
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
            movingAverageStrategyService.detectAndEnqueueTradingSignals();
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
            movingAverageStrategyService.managePositions();
        } catch (Exception e) {
            log.error("managePositions-error", e);
        }
    }
}
