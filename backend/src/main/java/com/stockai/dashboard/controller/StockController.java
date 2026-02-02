package com.stockai.dashboard.controller;

import com.stockai.dashboard.domain.dto.StockSearchResultDto;
import com.stockai.dashboard.domain.dto.StockUpdateMessage;
import com.stockai.dashboard.domain.dto.TopStockDto;
import com.stockai.dashboard.service.NaverStockService;
import com.stockai.dashboard.service.StockRankService;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class StockController {

    private final StockService stockService;
    private final NaverStockService naverStockService;
    private final StockSearchService stockSearchService;
    private final StockRankService stockRankService;

    @GetMapping("/recommended")
    public ResponseEntity<Map<String, Object>> getRecommendedStocks(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "aiScore") String sortBy) {
        log.info("[API] GET /api/stocks/recommended - limit={}, sortBy={}", limit, sortBy);

        // StockRankService의 AI 추천 종목 사용
        List<TopStockDto> topStocks = stockRankService.getTop10Stocks();

        // 기존 응답 형식에 맞게 변환
        List<Map<String, Object>> stocks = topStocks.stream()
                .limit(limit)
                .map(dto -> {
                    Map<String, Object> stock = new HashMap<>();
                    stock.put("symbol", dto.getStockCode());
                    stock.put("name", dto.getStockName());
                    stock.put("price", dto.getCurrentPrice());
                    stock.put("change", dto.getChangePercent() != null ?
                            (long)(dto.getCurrentPrice() * dto.getChangePercent() / (100 + dto.getChangePercent())) : 0);
                    stock.put("changePercent", dto.getChangePercent());
                    stock.put("marketCap", dto.getMarketCap());
                    stock.put("aiScore", dto.getAiScore());
                    stock.put("sentiment", dto.getAiScore() >= 70 ? "positive" :
                            dto.getAiScore() >= 50 ? "neutral" : "negative");
                    stock.put("recommendReason", generateRecommendReason(dto));
                    return stock;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("stocks", stocks);
        response.put("totalCount", stocks.size());
        response.put("lastUpdated", LocalDateTime.now());
        return ResponseEntity.ok(response);
    }

    /**
     * AI 추천 이유 생성 (한줄 요약)
     */
    private String generateRecommendReason(TopStockDto dto) {
        String code = dto.getStockCode();
        int aiScore = dto.getAiScore();
        double changePercent = dto.getChangePercent() != null ? dto.getChangePercent() : 0;

        // 섹터별 추천 이유
        Map<String, String> sectorReasons = new HashMap<>();
        sectorReasons.put("005930", "반도체 업황 회복 기대, AI 수요 증가");
        sectorReasons.put("000660", "메모리 가격 반등, HBM 수혜");
        sectorReasons.put("373220", "2차전지 수출 호조, 전기차 시장 확대");
        sectorReasons.put("006400", "배터리 양극재 수요 증가");
        sectorReasons.put("207940", "바이오 신약 파이프라인 기대");
        sectorReasons.put("068270", "세포·유전자 치료제 성장");
        sectorReasons.put("009150", "조선 수주 호황, 친환경 선박 수요");
        sectorReasons.put("010130", "고부가가치 선박 수주 증가");
        sectorReasons.put("005380", "전기차 라인업 확대, 북미 시장 확대");
        sectorReasons.put("000270", "전기차 전환 가속화");
        sectorReasons.put("035420", "플랫폼 성장, AI 사업 확대");
        sectorReasons.put("035720", "카카오 광고·커머스 성장");
        sectorReasons.put("051910", "화장품 해외 수출 증가");
        sectorReasons.put("005490", "철강 가격 회복 기대");
        sectorReasons.put("012330", "건설장비 수출 증가");
        sectorReasons.put("028260", "K-컨텐츠 해외 수출 호조");
        sectorReasons.put("259960", "IP 비즈니스 확대");
        sectorReasons.put("251270", "넷마블 게임 라인업 강화");

        // 미리 정의된 이유가 있으면 사용
        if (sectorReasons.containsKey(code)) {
            return sectorReasons.get(code);
        }

        // AI 점수와 등락률 기반 동적 생성
        if (aiScore >= 75) {
            if (changePercent > 0) {
                return "AI 고점수 + 상승 모멘텀";
            } else {
                return "AI 고점수, 저점 매수 기회";
            }
        } else if (aiScore >= 60) {
            if (changePercent > 2) {
                return "상승세 지속, 추가 상승 여력";
            } else if (changePercent < -2) {
                return "기술적 반등 구간";
            } else {
                return "안정적 수익 기대";
            }
        } else {
            return "분산 투자 관점에서 관심";
        }
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
