package com.iedev.pdftoolkit.job.worker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class WorkerSchedulerConfig {

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService workerScheduler() {
        return Executors.newScheduledThreadPool(2);
    }
}