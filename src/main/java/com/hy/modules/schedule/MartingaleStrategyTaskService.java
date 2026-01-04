package com.hy.modules.schedule;

import com.hy.modules.cex.service.MartingaleStrategyService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "task.martingale", name = "enabled", havingValue = "true")
public class MartingaleStrategyTaskService {

    private final MartingaleStrategyService martingaleStrategyService;

    public MartingaleStrategyTaskService(MartingaleStrategyService martingaleStrategyService) {
        this.martingaleStrategyService = martingaleStrategyService;
    }

    /**
     * 初始化方法马丁策略
     */
    @PostConstruct
    public void init() {
        martingaleStrategyService.init();
    }

    /***
     * 加载配置
     * 每60秒执行一次
     **/
    @Scheduled(fixedDelay = 60000)
    public void loadConfig() {
        try {
            martingaleStrategyService.initializeConfig();
        } catch (Exception e) {
            log.error("loadConfig-error", e);
        }
    }

    /**
     * 启动马丁策略
     * 每1000毫秒执行一次
     * 即使上次没执行完，调度器也会按时间启动新任务（可能重叠）
     **/
    @Scheduled(fixedRate = 1000)
    public void startMartingaleStrategy() {
        try {
            martingaleStrategyService.startMartingaleStrategy();
        } catch (Exception e) {
            log.error("startMartingaleStrategy-error", e);
        }
    }

    /**
     * 仓位管理
     * 每两秒秒执行一次
     **/
    @Scheduled(fixedDelay = 2000)
    public void managePositions() {
        try {
            martingaleStrategyService.managePositions();
        } catch (Exception e) {
            log.error("managePositions-error", e);
        }
    }
}
