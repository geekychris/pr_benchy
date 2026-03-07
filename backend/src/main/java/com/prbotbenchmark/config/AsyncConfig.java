package com.prbotbenchmark.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Value("${app.run.max-concurrency:10}")
    private int maxConcurrency;

    @Bean(name = "runExecutor")
    public ThreadPoolTaskExecutor runExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(maxConcurrency);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("run-worker-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "replayExecutor")
    public ThreadPoolTaskExecutor replayExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(maxConcurrency * 2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("replay-worker-");
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return runExecutor();
    }
}
