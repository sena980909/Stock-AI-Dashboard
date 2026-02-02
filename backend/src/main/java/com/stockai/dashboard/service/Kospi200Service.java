package com.stockai.dashboard.service;

import com.stockai.dashboard.domain.dto.StockSearchResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 코스피 200 종목 서비스
 * 페이지네이션 및 캐싱 지원
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class Kospi200Service {

    private static final String REDIS_KEY_KOSPI200_PAGE = "kospi200:page:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final RedisTemplate<String, Object> redisTemplate;
    private final Kospi200DataService kospi200DataService;
    private final NaverStockService naverStockService;

    // 메모리 캐시 (Redis 실패 시 fallback)
    private final Map<String, List<StockSearchResultDto>> memoryCache = new ConcurrentHashMap<>();
    private volatile long lastCacheTime = 0;

    /**
     * 코스피 200 종목 페이지네이션 조회
     */
    public Map<String, Object> getKospi200Paginated(int page, int size) {
        log.info("[getKospi200Paginated] Fetching page {} with size {}", page, size);

        String cacheKey = REDIS_KEY_KOSPI200_PAGE + page + ":" + size;

        // 1. Redis 캐시 조회
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("[getKospi200Paginated] Cache HIT for page {}", page);
                @SuppressWarnings("unchecked")
                Map<String, Object> cachedResult = (Map<String, Object>) cached;
                return cachedResult;
            }
        } catch (Exception e) {
            log.warn("[getKospi200Paginated] Redis read failed: {}", e.getMessage());
        }

        log.info("[getKospi200Paginated] Cache MISS - fetching real-time data");

        // 2. 코스피 200 종목 목록 가져오기
        Map<String, String> allStocks = kospi200DataService.getStockCodeToName();
        List<String> stockCodes = new ArrayList<>(allStocks.keySet());

        int totalCount = stockCodes.size();
        int totalPages = (int) Math.ceil((double) totalCount / size);

        // 페이지 범위 계산
        int startIndex = (page - 1) * size;
        int endIndex = Math.min(startIndex + size, totalCount);

        if (startIndex >= totalCount) {
            return createEmptyResponse(page, size, totalCount, totalPages);
        }

        // 3. 해당 페이지 종목 데이터 조회
        List<StockSearchResultDto> pageData = new ArrayList<>();
        List<String> pageStockCodes = stockCodes.subList(startIndex, endIndex);

        for (String code : pageStockCodes) {
            String name = allStocks.get(code);
            StockSearchResultDto dto = fetchStockData(code, name);
            if (dto != null) {
                pageData.add(dto);
            }
        }

        // 4. 응답 생성
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", pageData);
        result.put("page", page);
        result.put("size", size);
        result.put("totalCount", totalCount);
        result.put("totalPages", totalPages);
        result.put("hasNext", page < totalPages);
        result.put("hasPrevious", page > 1);

        // 5. Redis에 캐싱
        try {
            redisTemplate.opsForValue().set(cacheKey, result, CACHE_TTL);
            log.debug("[getKospi200Paginated] Cached page {} to Redis", page);
        } catch (Exception e) {
            log.warn("[getKospi200Paginated] Redis write failed: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 개별 종목 실시간 데이터 조회
     */
    private StockSearchResultDto fetchStockData(String code, String name) {
        try {
            Map<String, Object> stockDetail = naverStockService.fetchStock(code);

            Long currentPrice = null;
            Double changeRate = null;
            Long marketCap = null;
            Integer aiScore = null;
            String signalType = null;

            if (stockDetail != null) {
                if (stockDetail.get("price") != null) {
                    currentPrice = ((Number) stockDetail.get("price")).longValue();
                }
                if (stockDetail.get("changePercent") != null) {
                    changeRate = ((Number) stockDetail.get("changePercent")).doubleValue();
                }
                if (stockDetail.get("marketCap") != null) {
                    marketCap = ((Number) stockDetail.get("marketCap")).longValue();
                }
                if (stockDetail.get("aiScore") != null) {
                    aiScore = ((Number) stockDetail.get("aiScore")).intValue();
                }
                if (name == null && stockDetail.get("name") != null) {
                    name = stockDetail.get("name").toString();
                }
            }

            // AI 점수 기반 신호 결정
            if (aiScore != null) {
                if (aiScore >= 70) signalType = "BUY";
                else if (aiScore >= 50) signalType = "NEUTRAL";
                else signalType = "SELL";
            }

            return StockSearchResultDto.builder()
                    .code(code)
                    .name(name)
                    .market("KOSPI")
                    .currentPrice(currentPrice)
                    .changeRate(changeRate)
                    .marketCap(marketCap)
                    .aiScore(aiScore)
                    .signalType(signalType)
                    .build();

        } catch (Exception e) {
            log.warn("[fetchStockData] Failed for {}: {}", code, e.getMessage());
            return StockSearchResultDto.builder()
                    .code(code)
                    .name(name)
                    .market("KOSPI")
                    .build();
        }
    }

    /**
     * 빈 응답 생성
     */
    private Map<String, Object> createEmptyResponse(int page, int size, int totalCount, int totalPages) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", Collections.emptyList());
        result.put("page", page);
        result.put("size", size);
        result.put("totalCount", totalCount);
        result.put("totalPages", totalPages);
        result.put("hasNext", false);
        result.put("hasPrevious", page > 1);
        return result;
    }

    /**
     * 전체 종목 수
     */
    public int getTotalCount() {
        return kospi200DataService.getTotalCount();
    }

    /**
     * 캐시 새로고침
     */
    public void refreshCache() {
        log.info("[refreshCache] Clearing KOSPI 200 cache");
        try {
            Set<String> keys = redisTemplate.keys(REDIS_KEY_KOSPI200_PAGE + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("[refreshCache] Deleted {} cache keys", keys.size());
            }
        } catch (Exception e) {
            log.warn("[refreshCache] Failed to clear cache: {}", e.getMessage());
        }
        memoryCache.clear();
    }

    /**
     * 5분마다 자동 캐시 갱신
     */
    @Scheduled(fixedRate = 300000)
    public void scheduledCacheRefresh() {
        log.debug("[scheduledCacheRefresh] Running scheduled cache refresh");
        refreshCache();
    }
}
