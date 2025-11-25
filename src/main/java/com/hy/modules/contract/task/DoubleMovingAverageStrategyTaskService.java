package com.hy.modules.contract.task;

import com.hy.modules.contract.service.DoubleMovingAverageStrategyService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "task.doublemovingaverage", name = "enabled", havingValue = "true")
public class DoubleMovingAverageStrategyTaskService {

    private final DoubleMovingAverageStrategyService doubleMovingAverageStrategyService;

    public DoubleMovingAverageStrategyTaskService(DoubleMovingAverageStrategyService doubleMovingAverageStrategyService) {
        this.doubleMovingAverageStrategyService = doubleMovingAverageStrategyService;
    }

    /**
     * 初始化方法双均线策略
     */
    @PostConstruct
    public void init() {
        //启动时执行一次
        doubleMovingAverageDataMonitoring();
        doubleMovingAverageStrategyService.init();
    }

    /**
     * 双均线数据监控
     * 每小时的第 1 分钟执行一次
     **/
    @Scheduled(cron = "0 1 * * * ?")
    public void doubleMovingAverageDataMonitoring() {
        try {
            doubleMovingAverageStrategyService.doubleMovingAverageDataMonitoring();
        } catch (Exception e) {
            log.error("doubleMovingAverageDataMonitoring-error", e);
        }
    }

    /**
     * 行情数据监控 每5秒执行一次
     **/
    @Scheduled(fixedRate = 5000)
    public void marketDataMonitoring() {
        try {
            doubleMovingAverageStrategyService.marketDataMonitoring();
        } catch (Exception e) {
            log.error("marketDataMonitoring-error", e);
        }
    }

    /**
     * 双均线策略信号下单监控
     * 每500毫秒执行一次
     * 即使上次没执行完，调度器也会按时间启动新任务（可能重叠）
     **/
    @Scheduled(fixedRate = 500)
    public void signalOrderMonitoring() {
        try {
            doubleMovingAverageStrategyService.signalOrderMonitoring();
        } catch (Exception e) {
            log.error("signalOrderMonitoring-error", e);
        }
    }

    /**
     * 仓位管理
     * 每秒执行一次
     **/
    @Scheduled(fixedDelay = 1000)
    public void managePositions() {
        try {
            doubleMovingAverageStrategyService.managePositions();
        } catch (Exception e) {
            log.error("managePositions-error", e);
        }
    }
}
