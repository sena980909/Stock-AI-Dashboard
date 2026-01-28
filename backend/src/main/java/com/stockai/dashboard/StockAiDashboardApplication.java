package com.stockai.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StockAiDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockAiDashboardApplication.class, args);
    }
}
