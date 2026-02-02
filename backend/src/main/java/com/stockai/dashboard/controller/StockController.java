package com.stockai.dashboard.controller;

import com.stockai.dashboard.domain.dto.StockSearchResultDto;
import com.stockai.dashboard.domain.dto.StockUpdateMessage;
import com.stockai.dashboard.service.NaverStockService;
import com.stockai.dashboard.service.StockSearchService;
import com.stockai.dashboard.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class StockController {

    private final StockService stockService;
    private final NaverStockService naverStockService;
    private final StockSearchService stockSearchService;

    @GetMapping("/recommended")
    public ResponseEntity<Map<String, Object>> getRecommendedStocks(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "aiScore") String sortBy) {
        log.info("[API] GET /api/stocks/recommended - limit={}, sortBy={}", limit, sortBy);
        List<Map<String, Object>> stocks = naverStockService.fetchRealTimeStocks();
        Map<String, Object> response = new HashMap<>();
        response.put("stocks", stocks.subList(0, Math.min(limit, stocks.size())));
        response.put("totalCount", stocks.size());
        response.put("lastUpdated", LocalDateTime.now());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{symbol}/current")
    public ResponseEntity<StockUpdateMessage> getCurrentPrice(@PathVariable String symbol) {
        log.info("[API] GET /api/stocks/{}/current", symbol);
        return ResponseEntity.ok(stockService.getCurrentPrice(symbol));
    }

    @GetMapping("/{symbol}/analysis")
    public ResponseEntity<Map<String, Object>> getAIAnalysis(@PathVariable String symbol) {
        log.info("[API] GET /api/stocks/{}/analysis", symbol);
        return ResponseEntity.ok(stockService.getAIAnalysis(symbol));
    }

    /**
     * 종목 검색 API
     * 종목명 또는 종목코드로 검색
     *
     * @param keyword 검색어 (종목명 또는 종목코드)
     * @param query 검색어 (keyword와 동일, 호환성 유지)
     * @return 검색 결과 리스트
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchStocks(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "query", required = false) String query) {

        // keyword 또는 query 중 하나 사용
        String searchTerm = keyword != null ? keyword : query;
        log.info("[API] GET /api/stocks/search - keyword={}", searchTerm);

        // 검색어 유효성 검사
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", List.of());
            response.put("count", 0);
            response.put("message", "Please enter a search keyword");
            return ResponseEntity.ok(response);
        }

        try {
            List<StockSearchResultDto> results = stockSearchService.searchStocks(searchTerm.trim());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", results);
            response.put("count", results.size());
            response.put("keyword", searchTerm.trim());
            response.put("message", results.isEmpty() ?
                    "No results found for: " + searchTerm :
                    "Found " + results.size() + " results");

            log.info("[API] GET /api/stocks/search - Found {} results for keyword: {}",
                    results.size(), searchTerm);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[API] GET /api/stocks/search - Error: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("data", List.of());
            errorResponse.put("count", 0);
            errorResponse.put("message", "Search failed: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 종목 코드로 직접 조회
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<Map<String, Object>> getStockByCode(@PathVariable("code") String code) {
        log.info("[API] GET /api/stocks/code/{}", code);

        if (code == null || code.trim().isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("data", null);
            errorResponse.put("message", "Invalid stock code");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        try {
            StockSearchResultDto result = stockSearchService.getStockByCode(code.trim());

            if (result == null) {
                Map<String, Object> notFoundResponse = new HashMap<>();
                notFoundResponse.put("success", false);
                notFoundResponse.put("data", null);
                notFoundResponse.put("message", "Stock not found: " + code);
                return ResponseEntity.status(404).body(notFoundResponse);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", result);
            response.put("message", "Stock found");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[API] GET /api/stocks/code/{} - Error: {}", code, e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("data", null);
            errorResponse.put("message", "Failed to get stock: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 인기 검색어 조회
     */
    @GetMapping("/search/popular")
    public ResponseEntity<Map<String, Object>> getPopularSearchTerms() {
        log.info("[API] GET /api/stocks/search/popular");

        try {
            List<String> popularTerms = stockSearchService.getPopularSearchTerms();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", popularTerms);
            response.put("count", popularTerms.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[API] GET /api/stocks/search/popular - Error: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("data", List.of());
            errorResponse.put("message", "Failed to get popular terms");

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
