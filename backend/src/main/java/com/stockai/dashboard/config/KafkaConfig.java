package com.stockai.dashboard.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic stockPriceTopic() {
        return TopicBuilder.name("stock-price")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic newsSentimentTopic() {
        return TopicBuilder.name("news-sentiment")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
