package com.hy.modules.contract.task;

import com.hy.modules.contract.service.DualMovingAverageStrategyService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
//@Service
public class DualMovingAverageStrategyTaskService {

    private final DualMovingAverageStrategyService dualMovingAverageStrategyService;

    public DualMovingAverageStrategyTaskService(DualMovingAverageStrategyService dualMovingAverageStrategyService) {
        this.dualMovingAverageStrategyService = dualMovingAverageStrategyService;
    }

    /**
     * 初始化方法
     * 在Spring容器启动时调用
     */
    @PostConstruct
    public void init() {
        dualMovingAverageStrategyService.start();
    }

    /*
     * @Scheduled(fixedRate = 1000)   // 即使上次没执行完，调度器也会按时间启动新任务（可能重叠）
     * @Scheduled(fixedDelay = 1000)  // 等上一次执行完，再等1秒
     **/

    /**
     * 双均线信号监控
     * 每10秒执行一次
     **/
    @Scheduled(fixedRate = 10000)
    public void dualMovingAverageSignalMonitoring() {
        try {
            dualMovingAverageStrategyService.dualMovingAverageSignalMonitoring();
        } catch (Exception e) {
            log.error("dualMovingAverageSignalMonitoring-error", e);
        }
    }

    /**
     * 获取行情数据
     * 每秒执行一次
     */
    @Scheduled(fixedRate = 1000)
    public void marketDataMonitoring() {
        try {
            dualMovingAverageStrategyService.marketDataMonitoring();
        } catch (Exception e) {
            log.error("marketDataMonitoring-error", e);
        }
    }

    /**
     * 双均线信号下单
     * 每200毫秒执行一次
     * 即使上次没执行完，调度器也会按时间启动新任务（可能重叠）
     **/
    @Scheduled(fixedRate = 200)
    public void signalOrderMonitoring() {
        try {
            dualMovingAverageStrategyService.signalOrderMonitoring();
        } catch (Exception e) {
            log.error("signalOrderMonitoring-error", e);
        }
    }

    /**
     * 仓位管理
     * 每两秒秒执行一次
     **/
    @Scheduled(fixedDelay = 2000)
    public void positionManagement() {
        try {
            dualMovingAverageStrategyService.positionManagement();
        } catch (Exception e) {
            log.error("positionManagement-error", e);
        }
    }

}
