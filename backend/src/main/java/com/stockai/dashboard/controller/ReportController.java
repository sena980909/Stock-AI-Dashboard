package com.stockai.dashboard.controller;

import com.stockai.dashboard.domain.dto.AiPerformanceDto;
import com.stockai.dashboard.service.StockBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class ReportController {

    private final StockBatchService stockBatchService;

    /**
     * AI 추천 성과 통계 조회
     * GET /api/reports/performance?days=30
     */
    @GetMapping("/performance")
    public ResponseEntity<Map<String, Object>> getAiPerformance(
            @RequestParam(defaultValue = "30") int days) {

        log.info("[API] GET /api/reports/performance - days={}", days);

        try {
            // 기간 제한 (최대 90일)
            if (days > 90) days = 90;
            if (days < 7) days = 7;

            AiPerformanceDto performance = stockBatchService.getPerformanceStats(days);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", performance);
            response.put("message", String.format("최근 %d일간 AI 추천 성과", days));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[API] GET /api/reports/performance - Error: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "성과 데이터 조회 실패: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 수동으로 오늘의 추천 저장 (관리자용)
     * POST /api/reports/recommendations/save
     */
    @PostMapping("/recommendations/save")
    public ResponseEntity<Map<String, Object>> saveRecommendations() {
        log.info("[API] POST /api/reports/recommendations/save");

        try {
            int savedCount = stockBatchService.saveRecommendationsManual();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("savedCount", savedCount);
            response.put("message", String.format("오늘의 추천 %d건 저장 완료", savedCount));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[API] POST /api/reports/recommendations/save - Error: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "추천 저장 실패: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 수동으로 수익률 업데이트 (관리자용)
     * POST /api/reports/performance/update
     */
    @PostMapping("/performance/update")
    public ResponseEntity<Map<String, Object>> updatePerformance() {
        log.info("[API] POST /api/reports/performance/update");

        try {
            stockBatchService.updatePerformanceManual();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "수익률 업데이트 완료");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[API] POST /api/reports/performance/update - Error: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "수익률 업데이트 실패: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 테스트용 샘플 데이터 생성 (개발용)
     * POST /api/reports/sample-data?days=30
     */
    @PostMapping("/sample-data")
    public ResponseEntity<Map<String, Object>> generateSampleData(
            @RequestParam(defaultValue = "30") int days) {

        log.info("[API] POST /api/reports/sample-data - days={}", days);

        try {
            // 최대 60일로 제한
            if (days > 60) days = 60;

            stockBatchService.generateSampleData(days);

            AiPerformanceDto performance = stockBatchService.getPerformanceStats(days);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", String.format("%d일치 샘플 데이터 생성 완료", days));
            response.put("data", performance);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[API] POST /api/reports/sample-data - Error: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "샘플 데이터 생성 실패: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
