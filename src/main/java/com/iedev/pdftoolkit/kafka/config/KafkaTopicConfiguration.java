package com.iedev.pdftoolkit.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfiguration {

    @Bean
    public NewTopic conversionRequestsTopic() {

        return TopicBuilder.name("pdf-conversion-requests")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic conversionResultsTopic() {

        return TopicBuilder.name("pdf-conversion-results")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
