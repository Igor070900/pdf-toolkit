package com.iedev.pdftoolkit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "pdf-toolkit.worker")
public class WorkerProperties {

    private Duration leaseDuration;
    private Duration heartbeatInterval;
    private int maxAttempts;
    private Duration retryInitialDelay;
    private Duration retryMaxDelay;
}