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
    private static final String REDIS_KEY_SORTED_CODES = "kospi200:sorted";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration SORTED_CACHE_TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, Object> redisTemplate;
    private final Kospi200DataService kospi200DataService;
    private final NaverStockService naverStockService;

    // 시가총액 순 정렬된 종목 코드 캐시 (메모리)
    private volatile List<String> sortedStockCodes = null;
    private volatile long sortedCacheTime = 0;
    private static final long SORTED_CACHE_DURATION_MS = 10 * 60 * 1000; // 10분

    // 메모리 캐시 (Redis 실패 시 fallback)
    private final Map<String, List<StockSearchResultDto>> memoryCache = new ConcurrentHashMap<>();
    private volatile long lastCacheTime = 0;

    /**
     * 코스피 200 종목 페이지네이션 조회 (최적화 버전)
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

        log.info("[getKospi200Paginated] Cache MISS - fetching data for page {}", page);

        // 2. 시가총액 순 정렬된 종목 코드 가져오기 (캐싱됨)
        List<String> sortedCodes = getSortedStockCodes();
        Map<String, String> allStocks = kospi200DataService.getStockCodeToName();

        int totalCount = sortedCodes.size();
        int totalPages = (int) Math.ceil((double) totalCount / size);

        // 페이지 범위 계산
        int startIndex = (page - 1) * size;
        int endIndex = Math.min(startIndex + size, totalCount);

        if (startIndex >= totalCount) {
            return createEmptyResponse(page, size, totalCount, totalPages);
        }

        // 3. 해당 페이지 종목만 실시간 데이터 조회 (10개만!)
        List<String> pageStockCodes = sortedCodes.subList(startIndex, endIndex);
        List<StockSearchResultDto> pageData = new ArrayList<>();

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
            Double volume = null;
            Double foreignRate = null;

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
                if (stockDetail.get("volume") != null) {
                    volume = ((Number) stockDetail.get("volume")).doubleValue();
                }
                if (stockDetail.get("foreignRate") != null) {
                    foreignRate = ((Number) stockDetail.get("foreignRate")).doubleValue();
                }
                if (name == null && stockDetail.get("name") != null) {
                    name = stockDetail.get("name").toString();
                }
            }

            // AI 점수 동적 계산
            int aiScore = calculateDynamicAiScore(code, changeRate, marketCap, volume, foreignRate);
            String signalType = determineSignalType(aiScore, changeRate);

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
                    .aiScore(50) // 기본값
                    .signalType("NEUTRAL")
                    .build();
        }
    }

    /**
     * 동적 AI 점수 계산
     * 업종 성장성, 시가총액, 외국인 비율 등을 종합 분석
     */
    private int calculateDynamicAiScore(String code, Double changeRate, Long marketCap,
                                         Double volume, Double foreignRate) {
        int baseScore = 50;

        // 1. 업종 성장성 점수 (핵심 - 최대 20점)
        baseScore += getSectorGrowthScore(code);

        // 2. 시가총액 안정성 (대형주 가산점, 최대 15점)
        if (marketCap != null) {
            if (marketCap >= 100_000_000_000_000L) baseScore += 15;
            else if (marketCap >= 50_000_000_000_000L) baseScore += 12;
            else if (marketCap >= 20_000_000_000_000L) baseScore += 9;
            else if (marketCap >= 10_000_000_000_000L) baseScore += 6;
            else if (marketCap >= 5_000_000_000_000L) baseScore += 3;
            else if (marketCap >= 1_000_000_000_000L) baseScore += 1;
        }

        // 3. 외국인 보유 비율 (최대 10점)
        if (foreignRate != null) {
            if (foreignRate > 50) baseScore += 10;
            else if (foreignRate > 40) baseScore += 8;
            else if (foreignRate > 30) baseScore += 6;
            else if (foreignRate > 20) baseScore += 4;
            else if (foreignRate > 10) baseScore += 2;
        }

        // 4. 당일 모멘텀 (단기 지표 - 축소, 최대 ±5점)
        if (changeRate != null) {
            if (changeRate > 3.0) baseScore += 5;
            else if (changeRate > 1.0) baseScore += 3;
            else if (changeRate > 0) baseScore += 1;
            else if (changeRate > -2.0) baseScore -= 1;
            else if (changeRate > -5.0) baseScore -= 3;
            else baseScore -= 5;
        }

        // 5. 종목별 기본 투자매력도 (±5점)
        baseScore += getStockBaseScore(code);

        // 점수 범위 제한 (35~90)
        return Math.max(35, Math.min(90, baseScore));
    }

    /**
     * 업종별 성장성 점수
     */
    private int getSectorGrowthScore(String code) {
        // 반도체
        if (code.equals("005930") || code.equals("000660")) return 20;
        // 2차전지
        if (code.equals("373220") || code.equals("006400") || code.equals("051910")) return 18;
        // 바이오
        if (code.equals("207940") || code.equals("068270") || code.equals("326030")) return 16;
        // 조선
        if (code.equals("009540") || code.equals("010140") || code.equals("042660")) return 18;
        // 자동차
        if (code.equals("005380") || code.equals("000270") || code.equals("012330")) return 14;
        // IT플랫폼
        if (code.equals("035420") || code.equals("035720")) return 12;
        // 게임
        if (code.equals("259960") || code.equals("036570") || code.equals("251270") || code.equals("263750")) return 12;
        // 엔터
        if (code.equals("352820") || code.equals("041510")) return 10;
        // 철강
        if (code.equals("005490")) return 8;
        // 통신
        if (code.equals("017670") || code.equals("030200")) return 6;
        // 금융
        if (code.equals("105560") || code.equals("055550") || code.equals("086790") || code.equals("316140")) return 4;
        // 건설/유통/항공
        if (code.equals("000720") || code.equals("004170") || code.equals("003490")) return 3;

        // 기본값 (업종 미분류)
        return 8;
    }

    /**
     * 종목별 기본 투자매력도
     */
    private int getStockBaseScore(String code) {
        // 글로벌 1위 / 독보적 기술력
        if (code.equals("005930")) return 5;  // 삼성전자
        if (code.equals("000660")) return 5;  // SK하이닉스
        if (code.equals("373220")) return 4;  // LG에너지솔루션
        if (code.equals("207940")) return 4;  // 삼성바이오
        if (code.equals("009540")) return 4;  // HD한국조선해양
        if (code.equals("068270")) return 3;  // 셀트리온
        if (code.equals("005380")) return 3;  // 현대차
        if (code.equals("259960")) return 3;  // 크래프톤
        // 성장성 제한
        if (code.equals("105560") || code.equals("055550") || code.equals("086790")) return -3; // 금융

        return 0;
    }

    /**
     * 신호 타입 결정
     */
    private String determineSignalType(int aiScore, Double changeRate) {
        // 기본 점수 기반 판단
        if (aiScore >= 75) {
            return changeRate != null && changeRate > 2.0 ? "STRONG_BUY" : "BUY";
        } else if (aiScore >= 60) {
            return "BUY";
        } else if (aiScore >= 45) {
            return "NEUTRAL";
        } else if (aiScore >= 30) {
            return "SELL";
        } else {
            return changeRate != null && changeRate < -3.0 ? "STRONG_SELL" : "SELL";
        }
    }

    /**
     * 시가총액 순 정렬된 종목 코드 목록 (캐싱)
     * 10분마다 갱신
     */
    private List<String> getSortedStockCodes() {
        long now = System.currentTimeMillis();

        // 메모리 캐시 유효한 경우
        if (sortedStockCodes != null && (now - sortedCacheTime) < SORTED_CACHE_DURATION_MS) {
            log.debug("[getSortedStockCodes] Using memory cached sorted codes");
            return sortedStockCodes;
        }

        // Redis 캐시 확인
        try {
            Object cached = redisTemplate.opsForValue().get(REDIS_KEY_SORTED_CODES);
            if (cached != null) {
                @SuppressWarnings("unchecked")
                List<String> redisCached = (List<String>) cached;
                sortedStockCodes = redisCached;
                sortedCacheTime = now;
                log.debug("[getSortedStockCodes] Using Redis cached sorted codes");
                return sortedStockCodes;
            }
        } catch (Exception e) {
            log.warn("[getSortedStockCodes] Redis read failed: {}", e.getMessage());
        }

        // 캐시 없으면 새로 계산
        log.info("[getSortedStockCodes] Building sorted stock codes by market cap...");
        sortedStockCodes = buildSortedStockCodes();
        sortedCacheTime = now;

        // Redis에 저장
        try {
            redisTemplate.opsForValue().set(REDIS_KEY_SORTED_CODES, sortedStockCodes, SORTED_CACHE_TTL);
            log.info("[getSortedStockCodes] Cached {} sorted codes to Redis", sortedStockCodes.size());
        } catch (Exception e) {
            log.warn("[getSortedStockCodes] Redis write failed: {}", e.getMessage());
        }

        return sortedStockCodes;
    }

    /**
     * 시가총액 순 정렬된 종목 코드 목록 생성
     */
    private List<String> buildSortedStockCodes() {
        Map<String, String> allStocks = kospi200DataService.getStockCodeToName();
        List<Map.Entry<String, Long>> stockMarketCaps = new ArrayList<>();

        // 각 종목의 시가총액 조회
        for (String code : allStocks.keySet()) {
            try {
                Map<String, Object> stockDetail = naverStockService.fetchStock(code);
                Long marketCap = 0L;
                if (stockDetail != null && stockDetail.get("marketCap") != null) {
                    marketCap = ((Number) stockDetail.get("marketCap")).longValue();
                }
                stockMarketCaps.add(Map.entry(code, marketCap));
            } catch (Exception e) {
                stockMarketCaps.add(Map.entry(code, 0L));
            }
        }

        // 시가총액 기준 내림차순 정렬
        stockMarketCaps.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        // 정렬된 코드 목록 반환
        return stockMarketCaps.stream()
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());
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
                log.info("[refreshCache] Deleted {} page cache keys", keys.size());
            }
            // 정렬 캐시도 삭제
            redisTemplate.delete(REDIS_KEY_SORTED_CODES);
        } catch (Exception e) {
            log.warn("[refreshCache] Failed to clear cache: {}", e.getMessage());
        }
        memoryCache.clear();
        sortedStockCodes = null;
        sortedCacheTime = 0;
    }

    /**
     * 5분마다 페이지 캐시 갱신
     */
    @Scheduled(fixedRate = 300000)
    public void scheduledCacheRefresh() {
        log.debug("[scheduledCacheRefresh] Running scheduled page cache refresh");
        try {
            Set<String> keys = redisTemplate.keys(REDIS_KEY_KOSPI200_PAGE + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("[scheduledCacheRefresh] Failed: {}", e.getMessage());
        }
        memoryCache.clear();
    }

    /**
     * 10분마다 시가총액 순위 갱신 (백그라운드)
     */
    @Scheduled(fixedRate = 600000, initialDelay = 60000)
    public void scheduledSortedCodesRefresh() {
        log.info("[scheduledSortedCodesRefresh] Refreshing sorted stock codes in background...");
        try {
            List<String> newSortedCodes = buildSortedStockCodes();
            redisTemplate.opsForValue().set(REDIS_KEY_SORTED_CODES, newSortedCodes, SORTED_CACHE_TTL);
            sortedStockCodes = newSortedCodes;
            sortedCacheTime = System.currentTimeMillis();
            log.info("[scheduledSortedCodesRefresh] Updated {} sorted codes", newSortedCodes.size());
        } catch (Exception e) {
            log.error("[scheduledSortedCodesRefresh] Failed: {}", e.getMessage());
        }
    }

    /**
     * 애플리케이션 시작 시 정렬 캐시 초기화
     */
    @jakarta.annotation.PostConstruct
    public void initSortedCodes() {
        // 비동기로 초기화 (시작 지연 방지)
        new Thread(() -> {
            try {
                Thread.sleep(5000); // 5초 후 시작
                log.info("[initSortedCodes] Initializing sorted stock codes...");
                getSortedStockCodes();
                log.info("[initSortedCodes] Initialization complete");
            } catch (Exception e) {
                log.warn("[initSortedCodes] Failed: {}", e.getMessage());
            }
        }).start();
    }
}
