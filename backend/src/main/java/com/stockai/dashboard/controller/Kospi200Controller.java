package com.stockai.dashboard.controller;

import com.stockai.dashboard.domain.dto.StockSearchResultDto;
import com.stockai.dashboard.service.Kospi200Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 코스피 200 종목 API Controller
 */
@RestController
@RequestMapping("/api/kospi200")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class Kospi200Controller {

    private final Kospi200Service kospi200Service;

    /**
     * 코스피 200 종목 페이지네이션 조회
     * GET /api/kospi200?page=1&size=10
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getKospi200Stocks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("[API] GET /api/kospi200 - page={}, size={}", page, size);

        try {
            // 페이지 범위 검증
            if (page < 1) page = 1;
            if (size < 1) size = 10;
            if (size > 50) size = 50;

            Map<String, Object> result = kospi200Service.getKospi200Paginated(page, size);

            log.info("[API] GET /api/kospi200 - Returned page {} with {} items",
                    page, ((List<?>) result.get("data")).size());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[API] GET /api/kospi200 - Error: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to fetch KOSPI 200 stocks: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 코스피 200 전체 종목 수 조회
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> getKospi200Count() {
        log.info("[API] GET /api/kospi200/count");

        int count = kospi200Service.getTotalCount();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("totalCount", count);
        response.put("totalPages", (int) Math.ceil((double) count / 10));

        return ResponseEntity.ok(response);
    }

    /**
     * 코스피 200 캐시 새로고침
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshCache() {
        log.info("[API] POST /api/kospi200/refresh");

        try {
            kospi200Service.refreshCache();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Cache refreshed successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[API] POST /api/kospi200/refresh - Error: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to refresh cache");

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
