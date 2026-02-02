package com.stockai.dashboard.controller;

import com.stockai.dashboard.service.MarketSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class MarketController {

    private final MarketSummaryService marketSummaryService;

    /**
     * 오늘의 시장 요약 조회
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getMarketSummary() {
        log.info("[API] GET /api/market/summary");

        try {
            Map<String, Object> summary = marketSummaryService.getMarketSummary();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", summary);
            response.put("message", "Market summary retrieved successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[API] GET /api/market/summary - Error: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to get market summary: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
