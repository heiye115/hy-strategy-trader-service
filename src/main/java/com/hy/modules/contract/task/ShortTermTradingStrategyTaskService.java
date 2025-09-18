package com.hy.modules.contract.task;

import com.hy.modules.contract.service.ShortTermTradingStrategyService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
//@Service
public class ShortTermTradingStrategyTaskService {

    private final ShortTermTradingStrategyService shortTermTradingStrategyV1Service;

    public ShortTermTradingStrategyTaskService(ShortTermTradingStrategyService shortTermTradingStrategyV1Service) {
        this.shortTermTradingStrategyV1Service = shortTermTradingStrategyV1Service;
    }

    /**
     * 初始化方法
     * 在Spring容器启动时调用
     */
    @PostConstruct
    public void init() {
        shortTermTradingStrategyV1Service.start();
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
            shortTermTradingStrategyV1Service.startKlineMonitoring();
        } catch (Exception e) {
            log.error("kLineMonitoring-error", e);
        }
    }


    /**
     * 获取行情数据
     * 每秒执行一次
     */
    @Scheduled(fixedRate = 1000)
    public void marketDataMonitoring() {
        try {
            shortTermTradingStrategyV1Service.startMarketDataMonitoring();
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
            shortTermTradingStrategyV1Service.monitorTradingSignals();
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
            shortTermTradingStrategyV1Service.managePositions();
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
            shortTermTradingStrategyV1Service.sendRangePriceEmail();
        } catch (Exception e) {
            log.error("sendRangePriceEmail-error", e);
        }
    }


}
