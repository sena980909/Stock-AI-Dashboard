package com.stockai.dashboard.service;

import com.stockai.dashboard.domain.dto.StockSearchResultDto;
import com.stockai.dashboard.domain.entity.StockAnalysis;
import com.stockai.dashboard.repository.StockAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockSearchService {

    private static final String REDIS_KEY_SEARCH_PREFIX = "search:";
    private static final Duration CACHE_TTL_SEARCH = Duration.ofMinutes(5);
    private static final int MAX_SEARCH_RESULTS = 20;

    private final RedisTemplate<String, Object> redisTemplate;
    private final StockAnalysisRepository stockAnalysisRepository;
    private final NaverStockService naverStockService;
    private final StockRankService stockRankService;
    private final Kospi200DataService kospi200DataService;

    /**
     * 종목 검색 (종목명 또는 종목코드)
     */
    @Transactional(readOnly = true)
    public List<StockSearchResultDto> searchStocks(String keyword) {
        log.info("[searchStocks] Searching stocks with keyword: {}", keyword);

        if (keyword == null || keyword.trim().length() < 1) {
            log.warn("[searchStocks] Invalid keyword: {}", keyword);
            return Collections.emptyList();
        }

        String normalizedKeyword = keyword.trim();
        String cacheKey = REDIS_KEY_SEARCH_PREFIX + normalizedKeyword.toLowerCase();

        // 1. Redis 캐시 조회
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && cached instanceof List) {
                log.info("[searchStocks] Cache HIT for keyword: {}", normalizedKeyword);
                @SuppressWarnings("unchecked")
                List<LinkedHashMap<String, Object>> cachedList = (List<LinkedHashMap<String, Object>>) cached;
                return convertToSearchResultList(cachedList);
            }
        } catch (Exception e) {
            log.warn("[searchStocks] Redis cache read failed: {}", e.getMessage());
        }

        log.info("[searchStocks] Cache MISS - searching from KOSPI 200 and DB");

        List<StockSearchResultDto> results = new ArrayList<>();
        Set<String> existingCodes = new HashSet<>();

        // 2. 코스피 200 종목에서 검색 (우선)
        List<Map<String, String>> kospi200Results = kospi200DataService.searchByKeyword(normalizedKeyword);
        log.info("[searchStocks] Found {} matches in KOSPI 200 for keyword: {}", kospi200Results.size(), normalizedKeyword);

        for (Map<String, String> stock : kospi200Results) {
            if (results.size() >= MAX_SEARCH_RESULTS) break;

            String code = stock.get("code");
            if (existingCodes.contains(code)) continue;

            // 실시간 데이터 조회
            StockSearchResultDto dto = fetchStockWithRealTimeData(code, stock.get("name"), stock.get("market"));
            if (dto != null) {
                results.add(dto);
                existingCodes.add(code);
            }
        }

        // 3. DB에서 추가 검색 (코스피 200에 없는 종목)
        if (results.size() < MAX_SEARCH_RESULTS) {
            List<StockAnalysis> dbResults = stockAnalysisRepository.searchByKeyword(normalizedKeyword);
            for (StockAnalysis analysis : dbResults) {
                if (results.size() >= MAX_SEARCH_RESULTS) break;
                if (!existingCodes.contains(analysis.getStockCode())) {
                    results.add(convertToSearchResult(analysis));
                    existingCodes.add(analysis.getStockCode());
                }
            }
        }

        // 4. 네이버 API에서 검색 (추가 종목 보완)
        if (results.size() < MAX_SEARCH_RESULTS) {
            List<Map<String, Object>> naverResults = naverStockService.searchStocks(normalizedKeyword);

            for (Map<String, Object> naverStock : naverResults) {
                if (results.size() >= MAX_SEARCH_RESULTS) break;
                String code = (String) naverStock.get("code");
                if (!existingCodes.contains(code)) {
                    StockSearchResultDto dto = createSearchResultFromNaver(naverStock);
                    if (dto != null) {
                        results.add(dto);
                        existingCodes.add(code);
                    }
                }
            }
        }

        // 4. 정확히 코드 일치하는 종목 우선 정렬
        results.sort((a, b) -> {
            boolean aExact = a.getCode().equalsIgnoreCase(normalizedKeyword) ||
                    a.getName().equalsIgnoreCase(normalizedKeyword);
            boolean bExact = b.getCode().equalsIgnoreCase(normalizedKeyword) ||
                    b.getName().equalsIgnoreCase(normalizedKeyword);
            if (aExact && !bExact) return -1;
            if (!aExact && bExact) return 1;
            return 0;
        });

        // 5. 결과 제한
        if (results.size() > MAX_SEARCH_RESULTS) {
            results = results.subList(0, MAX_SEARCH_RESULTS);
        }

        // 6. Redis에 캐싱
        try {
            if (!results.isEmpty()) {
                redisTemplate.opsForValue().set(cacheKey, results, CACHE_TTL_SEARCH);
                log.debug("[searchStocks] Cached {} results for keyword: {}", results.size(), normalizedKeyword);
            }
        } catch (Exception e) {
            log.warn("[searchStocks] Redis cache write failed: {}", e.getMessage());
        }

        log.info("[searchStocks] Found {} results for keyword: {}", results.size(), normalizedKeyword);
        return results;
    }

    /**
     * 종목 코드로 직접 검색 (정확한 매칭)
     */
    @Transactional
    public StockSearchResultDto getStockByCode(String code) {
        log.info("[getStockByCode] Searching stock by code: {}", code);

        if (code == null || code.trim().isEmpty()) {
            return null;
        }

        String normalizedCode = code.trim();

        // 1. DB에서 조회
        Optional<StockAnalysis> analysisOpt = stockAnalysisRepository
                .findFirstByStockCodeOrderByUpdatedAtDesc(normalizedCode);

        if (analysisOpt.isPresent()) {
            return convertToSearchResult(analysisOpt.get());
        }

        // 2. DB에 없으면 네이버 API로 조회 후 저장
        try {
            Map<String, Object> naverData = naverStockService.fetchStock(normalizedCode);
            if (naverData != null && naverData.get("name") != null) {
                // 분석 데이터 생성 및 저장
                StockAnalysis analysis = stockRankService.generateAnalysisForStock(normalizedCode);
                return convertToSearchResult(analysis);
            }
        } catch (Exception e) {
            log.error("[getStockByCode] Failed to fetch stock {}: {}", normalizedCode, e.getMessage());
        }

        return null;
    }

    /**
     * 인기 검색어 (최근 검색된 종목)
     */
    public List<String> getPopularSearchTerms() {
        // TODO: 실제 검색 로그 기반으로 구현
        return Arrays.asList(
                "삼성전자", "SK하이닉스", "NAVER", "카카오", "현대차",
                "셀트리온", "삼성SDI", "LG에너지솔루션", "기아", "삼성바이오로직스"
        );
    }

    /**
     * StockAnalysis를 SearchResultDto로 변환
     */
    private StockSearchResultDto convertToSearchResult(StockAnalysis analysis) {
        return StockSearchResultDto.builder()
                .code(analysis.getStockCode())
                .name(analysis.getStockName())
                .market("KOSPI") // TODO: 실제 시장 정보 추가
                .currentPrice(analysis.getCurrentPrice())
                .changeRate(analysis.getChangePercent())
                .marketCap(analysis.getMarketCap())
                .aiScore(analysis.getTotalScore())
                .signalType(analysis.getSignalType())
                .build();
    }

    /**
     * 코스피 200 종목의 실시간 데이터 조회
     */
    private StockSearchResultDto fetchStockWithRealTimeData(String code, String name, String market) {
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
                // 이름이 없으면 API에서 가져온 이름 사용
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
                    .market(market != null ? market : "KOSPI")
                    .currentPrice(currentPrice)
                    .changeRate(changeRate)
                    .marketCap(marketCap)
                    .aiScore(aiScore)
                    .signalType(signalType)
                    .build();
        } catch (Exception e) {
            log.warn("[fetchStockWithRealTimeData] Failed to fetch data for {}: {}", code, e.getMessage());
            // 실시간 데이터 실패 시 기본 정보만 반환
            return StockSearchResultDto.builder()
                    .code(code)
                    .name(name)
                    .market(market != null ? market : "KOSPI")
                    .build();
        }
    }

    /**
     * 네이버 API 결과를 SearchResultDto로 변환
     */
    private StockSearchResultDto createSearchResultFromNaver(Map<String, Object> naverStock) {
        try {
            String code = (String) naverStock.get("code");
            String name = (String) naverStock.get("name");
            String market = (String) naverStock.get("market");

            return fetchStockWithRealTimeData(code, name, market);
        } catch (Exception e) {
            log.warn("[createSearchResultFromNaver] Failed to create result: {}", e.getMessage());
            return null;
        }
    }

    /**
     * LinkedHashMap 리스트를 SearchResultDto 리스트로 변환
     */
    @SuppressWarnings("unchecked")
    private List<StockSearchResultDto> convertToSearchResultList(List<LinkedHashMap<String, Object>> cachedList) {
        return cachedList.stream()
                .map(map -> StockSearchResultDto.builder()
                        .code((String) map.get("code"))
                        .name((String) map.get("name"))
                        .market((String) map.get("market"))
                        .currentPrice(map.get("currentPrice") != null ?
                                ((Number) map.get("currentPrice")).longValue() : null)
                        .changeRate(map.get("changeRate") != null ?
                                ((Number) map.get("changeRate")).doubleValue() : null)
                        .marketCap(map.get("marketCap") != null ?
                                ((Number) map.get("marketCap")).longValue() : null)
                        .aiScore(map.get("aiScore") != null ?
                                ((Number) map.get("aiScore")).intValue() : null)
                        .signalType((String) map.get("signalType"))
                        .build())
                .collect(Collectors.toList());
    }
}
