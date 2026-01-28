package com.stockai.dashboard.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        Map<String, String> services = new HashMap<>();

        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now().toString());

        // Database Check
        try (Connection conn = dataSource.getConnection()) {
            services.put("database", conn.isValid(1) ? "UP" : "DOWN");
        } catch (Exception e) {
            services.put("database", "DOWN");
        }

        // Redis Check
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            services.put("redis", "UP");
        } catch (Exception e) {
            services.put("redis", "DOWN");
        }

        // Kafka Check
        try {
            kafkaTemplate.getDefaultTopic();
            services.put("kafka", "UP");
        } catch (Exception e) {
            services.put("kafka", "DOWN");
        }

        response.put("services", services);

        boolean allUp = services.values().stream().allMatch("UP"::equals);
        if (!allUp) {
            response.put("status", "DEGRADED");
        }

        return ResponseEntity.ok(response);
    }
}
