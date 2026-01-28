package com.stockai.dashboard.controller;

import com.stockai.dashboard.domain.dto.HitRateDto;
import com.stockai.dashboard.domain.dto.RecommendationDto;
import com.stockai.dashboard.domain.dto.StockHitRateDto;
import com.stockai.dashboard.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 추천 및 적중률 분석 API Controller
 */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * 전체 적중률 조회
     * GET /api/recommendations/hit-rate
     */
    @GetMapping("/hit-rate")
    public ResponseEntity<HitRateDto> getOverallHitRate() {
        return ResponseEntity.ok(recommendationService.getOverallHitRate());
    }

    /**
     * 기간별 적중률 조회
     * GET /api/recommendations/hit-rate/period?start=2024-01-01T00:00:00&end=2024-12-31T23:59:59
     */
    @GetMapping("/hit-rate/period")
    public ResponseEntity<HitRateDto> getHitRateByPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(recommendationService.getHitRateByPeriod(start, end));
    }

    /**
     * 종목별 적중률 조회
     * GET /api/recommendations/hit-rate/stocks
     */
    @GetMapping("/hit-rate/stocks")
    public ResponseEntity<List<StockHitRateDto>> getHitRateByStock() {
        return ResponseEntity.ok(recommendationService.getHitRateByStock());
    }

    /**
     * 신호 유형별 적중률 조회 (BUY vs SELL)
     * GET /api/recommendations/hit-rate/signal-type
     */
    @GetMapping("/hit-rate/signal-type")
    public ResponseEntity<Map<String, HitRateDto>> getHitRateBySignalType() {
        return ResponseEntity.ok(recommendationService.getHitRateBySignalType());
    }

    /**
     * 적중률 요약 (대시보드용)
     * GET /api/recommendations/hit-rate/summary
     */
    @GetMapping("/hit-rate/summary")
    public ResponseEntity<Map<String, Object>> getHitRateSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("overall", recommendationService.getOverallHitRate());
        summary.put("bySignalType", recommendationService.getHitRateBySignalType());
        summary.put("byStock", recommendationService.getHitRateByStock());
        return ResponseEntity.ok(summary);
    }

    /**
     * 최근 추천 목록 조회
     * GET /api/recommendations/recent?days=7
     */
    @GetMapping("/recent")
    public ResponseEntity<List<RecommendationDto>> getRecentRecommendations(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(recommendationService.getRecentRecommendations(days));
    }

    /**
     * 종목별 추천 이력 조회
     * GET /api/recommendations/stock/{stockCode}
     */
    @GetMapping("/stock/{stockCode}")
    public ResponseEntity<List<RecommendationDto>> getRecommendationsByStock(
            @PathVariable String stockCode) {
        return ResponseEntity.ok(recommendationService.getRecommendationsByStock(stockCode));
    }
}
