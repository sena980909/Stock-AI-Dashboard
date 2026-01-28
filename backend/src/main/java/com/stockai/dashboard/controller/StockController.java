package com.stockai.dashboard.controller;

import com.stockai.dashboard.domain.dto.StockUpdateMessage;
import com.stockai.dashboard.domain.entity.AIAnalysis;
import com.stockai.dashboard.domain.entity.Stock;
import com.stockai.dashboard.domain.entity.StockPrice;
import com.stockai.dashboard.service.NaverStockService;
import com.stockai.dashboard.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final NaverStockService naverStockService;

    @GetMapping("/recommended")
    public ResponseEntity<Map<String, Object>> getRecommendedStocks(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "aiScore") String sortBy) {
        List<Map<String, Object>> stocks = naverStockService.fetchRealTimeStocks();
        Map<String, Object> response = new HashMap<>();
        response.put("stocks", stocks.subList(0, Math.min(limit, stocks.size())));
        response.put("totalCount", stocks.size());
        response.put("lastUpdated", LocalDateTime.now());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{symbol}/current")
    public ResponseEntity<StockUpdateMessage> getCurrentPrice(@PathVariable String symbol) {
        return ResponseEntity.ok(stockService.getCurrentPrice(symbol));
    }

    @GetMapping("/{symbol}/history")
    public ResponseEntity<Map<String, Object>> getStockHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1M") String period,
            @RequestParam(defaultValue = "1d") String interval) {
        return ResponseEntity.ok(stockService.getStockHistory(symbol, period, interval));
    }

    @GetMapping("/{symbol}/analysis")
    public ResponseEntity<Map<String, Object>> getAIAnalysis(@PathVariable String symbol) {
        return ResponseEntity.ok(stockService.getAIAnalysis(symbol));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Stock>> searchStocks(@RequestParam String query) {
        return ResponseEntity.ok(stockService.searchStocks(query));
    }
}
