package com.stockai.dashboard.controller;

import com.stockai.dashboard.domain.dto.AiReportDto;
import com.stockai.dashboard.domain.dto.TopStockDto;
import com.stockai.dashboard.service.StockRankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class StockRankController {

    private final StockRankService stockRankService;

    /**
     * 시가총액 Top 10 종목 조회
     * Redis 캐시에서 조회하며, 캐시 미스 시 DB 조회 후 캐싱
     *
     * @return Top 10 종목 리스트 (AI 점수, 한줄평 포함)
     */
    @GetMapping("/top-rank")
    public ResponseEntity<Map<String, Object>> getTopRankStocks() {
        log.info("[API] GET /api/stocks/top-rank - Requesting top 10 stocks");

        try {
            List<TopStockDto> topStocks = stockRankService.getTop10Stocks();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", topStocks);
            response.put("count", topStocks.size());
            response.put("message", "Top 10 stocks retrieved successfully");

            log.info("[API] GET /api/stocks/top-rank - Returned {} stocks", topStocks.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[API] GET /api/stocks/top-rank - Error: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("data", null);
            errorResponse.put("message", "Failed to retrieve top stocks: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 특정 종목의 AI 상세 리포트 조회
     * SWOT 분석, 기술적 지표, 감성 분석 데이터 포함
     *
     * @param code 종목 코드 (예: 005930)
     * @return AI 상세 리포트
     */
    @GetMapping("/{code}/ai-report")
    public ResponseEntity<Map<String, Object>> getAiReport(@PathVariable("code") String code) {
        log.info("[API] GET /api/stocks/{}/ai-report - Requesting AI report", code);

        // 종목 코드 유효성 검사
        if (code == null || code.isBlank() || code.length() > 20) {
            log.warn("[API] GET /api/stocks/{}/ai-report - Invalid stock code", code);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("data", null);
            errorResponse.put("message", "Invalid stock code");

            return ResponseEntity.badRequest().body(errorResponse);
        }

        try {
            AiReportDto report = stockRankService.getAiReport(code);

            if (report == null || report.getStockCode() == null) {
                log.warn("[API] GET /api/stocks/{}/ai-report - Stock not found", code);

                Map<String, Object> notFoundResponse = new HashMap<>();
                notFoundResponse.put("success", false);
                notFoundResponse.put("data", null);
                notFoundResponse.put("message", "Stock not found: " + code);

                return ResponseEntity.status(404).body(notFoundResponse);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            response.put("message", "AI report retrieved successfully");

            log.info("[API] GET /api/stocks/{}/ai-report - Report retrieved, score={}",
                    code, report.getTotalScore());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[API] GET /api/stocks/{}/ai-report - Error: {}", code, e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("data", null);
            errorResponse.put("message", "Failed to retrieve AI report: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Top 10 캐시 강제 갱신 (관리자용)
     */
    @PostMapping("/top-rank/refresh")
    public ResponseEntity<Map<String, Object>> refreshTopRankCache() {
        log.info("[API] POST /api/stocks/top-rank/refresh - Refreshing top 10 cache");

        try {
            stockRankService.refreshTop10Cache();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Top 10 cache refreshed successfully");

            log.info("[API] POST /api/stocks/top-rank/refresh - Cache refreshed");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[API] POST /api/stocks/top-rank/refresh - Error: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to refresh cache: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 특정 종목 캐시 무효화 (관리자용)
     */
    @DeleteMapping("/{code}/cache")
    public ResponseEntity<Map<String, Object>> invalidateCache(@PathVariable("code") String code) {
        log.info("[API] DELETE /api/stocks/{}/cache - Invalidating cache", code);

        try {
            stockRankService.invalidateCache(code);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Cache invalidated for stock: " + code);

            log.info("[API] DELETE /api/stocks/{}/cache - Cache invalidated", code);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[API] DELETE /api/stocks/{}/cache - Error: {}", code, e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to invalidate cache: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
