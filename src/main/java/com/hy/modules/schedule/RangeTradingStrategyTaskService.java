package com.hy.modules.schedule;

import com.hy.modules.cex.service.RangeTradingStrategyService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "task.range", name = "enabled", havingValue = "true")
public class RangeTradingStrategyTaskService {

    private final RangeTradingStrategyService rangeTradingStrategyService;

    public RangeTradingStrategyTaskService(RangeTradingStrategyService rangeTradingStrategyService) {
        this.rangeTradingStrategyService = rangeTradingStrategyService;
    }

    /**
     * 初始化方法
     * 在Spring容器启动时调用
     */
    @PostConstruct
    public void init() {
        rangeTradingStrategyService.start();
    }

    /*
     * @Scheduled(fixedRate = 1000)   // 即使上次没执行完，调度器也会按时间启动新任务（可能重叠）
     * @Scheduled(fixedDelay = 1000)  // 等上一次执行完，再等1秒
     **/

    /**
     * K线监控
     * 每10秒执行一次
     **/
    @Scheduled(fixedRate = 10000)
    public void kLineMonitoring() {
        try {
            rangeTradingStrategyService.startKlineMonitoring();
        } catch (Exception e) {
            log.error("kLineMonitoring-error", e);
        }
    }

    /**
     * 历史K线监控
     * 每天凌晨执行一次
     **/
    @Scheduled(cron = "0 0 0 * * ?")
    public void historicalKLineMonitoring() {
        try {
            rangeTradingStrategyService.startHistoricalKlineMonitoring();
        } catch (Exception e) {
            log.error("historicalKLineMonitoring-error", e);
        }
    }

    /**
     * 获取行情数据
     * 每秒执行一次
     */
    @Scheduled(fixedRate = 1000)
    public void marketDataMonitoring() {
        try {
            rangeTradingStrategyService.startMarketDataMonitoring();
        } catch (Exception e) {
            log.error("marketDataMonitoring-error", e);
        }
    }

    /**
     * 信号下单
     * 每200毫秒执行一次
     * 即使上次没执行完，调度器也会按时间启动新任务（可能重叠）
     **/
    @Scheduled(fixedRate = 200)
    public void signalOrderMonitoring() {
        try {
            rangeTradingStrategyService.monitorTradingSignals();
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
            rangeTradingStrategyService.managePositions();
        } catch (Exception e) {
            log.error("positionManagement-error", e);
        }
    }

    /**
     * 区间价格信息定时发送邮件
     * 每8小时执行一次
     **/
    @Scheduled(cron = "0 0 0/8 * * ?")
    //@Scheduled(fixedRate = 20000)
    public void sendRangePriceEmail() {
        try {
            rangeTradingStrategyService.sendRangePriceEmail();
        } catch (Exception e) {
            log.error("sendRangePriceEmail-error", e);
        }
    }


}
