package com.hy.common.config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

//@Configuration
//@EnableAsync
public class AsyncConfig implements AsyncConfigurer {


    @Primary
    @Override
    @Bean//(name = "virtualThreadExecutor")
    public Executor getAsyncExecutor() {
        ThreadFactory virtualThreadFactory = Thread.ofVirtual().name("strategy-vt-", 0).factory();
        return Executors.newThreadPerTaskExecutor(virtualThreadFactory);
    }

//    @Primary
//    @Bean
//    public TaskExecutor virtualExecutor() {
//        Executor virtualExecutor = Executors.newThreadPerTaskExecutor(
//                Thread.ofVirtual().name("vt-", 0).factory()
//        );
//        return new TaskExecutorAdapter(virtualExecutor);
//    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // 简单的异步异常处理器
        return (throwable, method, objects) -> {
            System.err.println("Async error in method: " + method.getName());
            throwable.printStackTrace();
        };
    }
}
