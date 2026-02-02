package com.stockai.dashboard.controller;

import com.stockai.dashboard.service.NewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 뉴스 API Controller
 */
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class NewsController {

    private final NewsService newsService;

    /**
     * 최신 뉴스 조회
     * GET /api/news/latest
     */
    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> getLatestNews() {
        log.info("[API] GET /api/news/latest");

        try {
            List<Map<String, Object>> news = newsService.getLatestNews();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", news);
            response.put("count", news.size());

            log.info("[API] GET /api/news/latest - Returned {} news items", news.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[API] GET /api/news/latest - Error: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("data", List.of());
            errorResponse.put("message", "Failed to fetch news");

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 뉴스 캐시 새로고침
     * POST /api/news/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshNews() {
        log.info("[API] POST /api/news/refresh");

        try {
            newsService.refreshCache();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "News cache refreshed");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[API] POST /api/news/refresh - Error: {}", e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to refresh news cache");

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
