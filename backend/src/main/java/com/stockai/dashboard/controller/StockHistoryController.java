package com.stockai.dashboard.controller;

import com.stockai.dashboard.domain.dto.StockHistoryDto;
import com.stockai.dashboard.service.StockHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 일봉 데이터 API Controller
 */
@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockHistoryController {

    private final StockHistoryService stockHistoryService;

    /**
     * 일봉 데이터 조회
     * GET /api/stocks/{code}/history?days=30
     */
    @GetMapping("/{code}/history")
    public ResponseEntity<Map<String, Object>> getStockHistory(
            @PathVariable String code,
            @RequestParam(defaultValue = "30") int days) {

        List<StockHistoryDto> history = stockHistoryService.getHistory(code, days);

        Map<String, Object> response = new HashMap<>();
        response.put("stockCode", code);
        response.put("days", days);
        response.put("count", history.size());
        response.put("data", history);

        return ResponseEntity.ok(response);
    }

    /**
     * 수동 데이터 수집 (관리자용)
     * POST /api/stocks/{code}/history/collect
     */
    @PostMapping("/{code}/history/collect")
    public ResponseEntity<Map<String, String>> collectHistory(@PathVariable String code) {
        stockHistoryService.collectHistoryForStock(code);

        Map<String, String> response = new HashMap<>();
        response.put("message", "History collection started for " + code);
        return ResponseEntity.ok(response);
    }
}
