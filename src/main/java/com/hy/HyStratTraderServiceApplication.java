package com.hy;

import cn.hutool.extra.spring.EnableSpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@EnableSpringUtil
@EnableScheduling
@SpringBootApplication
public class HyStratTraderServiceApplication {

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(HyStratTraderServiceApplication.class, args);
        log.info("--------------------启动成功!--------------------");
    }

}
