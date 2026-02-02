package com.stockai.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockai.dashboard.domain.dto.AiReportDto;
import com.stockai.dashboard.domain.dto.TopStockDto;
import com.stockai.dashboard.domain.entity.StockAnalysis;
import com.stockai.dashboard.repository.StockAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockRankService {

    private static final String REDIS_KEY_TOP10 = "dashboard:top10";
    private static final String REDIS_KEY_AI_REPORT_PREFIX = "ai:report:";
    private static final Duration CACHE_TTL_TOP10 = Duration.ofMinutes(10);
    private static final Duration CACHE_TTL_REPORT = Duration.ofMinutes(30);

    private final RedisTemplate<String, Object> redisTemplate;
    private final StockAnalysisRepository stockAnalysisRepository;
    private final NaverStockService naverStockService;
    private final ObjectMapper objectMapper;

    // 대한민국 시가총액 상위 10개 종목 코드 (2024년 기준)
    private static final List<String> TOP_10_STOCK_CODES = Arrays.asList(
            "005930", // 삼성전자
            "000660", // SK하이닉스
            "373220", // LG에너지솔루션
            "207940", // 삼성바이오로직스
            "005380", // 현대차
            "000270", // 기아
            "068270", // 셀트리온
            "035420", // NAVER
            "005490", // POSCO홀딩스
            "035720"  // 카카오
    );

    /**
     * 시가총액 Top 10 종목 조회 (Redis Cache-Aside 패턴)
     */
    @Transactional(readOnly = true)
    public List<TopStockDto> getTop10Stocks() {
        log.info("[getTop10Stocks] Fetching top 10 stocks by market cap");

        // 1. Redis 캐시 조회
        try {
            Object cached = redisTemplate.opsForValue().get(REDIS_KEY_TOP10);
            if (cached != null) {
                log.info("[getTop10Stocks] Cache HIT - returning cached data");
                if (cached instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<LinkedHashMap<String, Object>> cachedList = (List<LinkedHashMap<String, Object>>) cached;
                    return convertToTopStockDtoList(cachedList);
                }
            }
        } catch (Exception e) {
            log.warn("[getTop10Stocks] Redis cache read failed: {}", e.getMessage());
        }

        log.info("[getTop10Stocks] Cache MISS - fetching from database");

        // 2. DB에서 조회
        List<StockAnalysis> analyses = stockAnalysisRepository.findTop10ByMarketCapDesc();

        // 3. DB에 데이터가 없으면 초기 데이터 생성
        if (analyses.isEmpty()) {
            log.info("[getTop10Stocks] No data in DB - generating initial analysis data");
            analyses = generateInitialAnalysisData();
        }

        // 4. DTO 변환
        AtomicInteger rank = new AtomicInteger(1);
        List<TopStockDto> result = analyses.stream()
                .map(analysis -> TopStockDto.from(analysis, rank.getAndIncrement()))
                .collect(Collectors.toList());

        // 5. Redis에 저장
        try {
            redisTemplate.opsForValue().set(REDIS_KEY_TOP10, result, CACHE_TTL_TOP10);
            log.info("[getTop10Stocks] Cached {} stocks to Redis with TTL {} minutes",
                    result.size(), CACHE_TTL_TOP10.toMinutes());
        } catch (Exception e) {
            log.warn("[getTop10Stocks] Redis cache write failed: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 특정 종목의 AI 상세 리포트 조회
     */
    @Transactional(readOnly = true)
    public AiReportDto getAiReport(String stockCode) {
        log.info("[getAiReport] Fetching AI report for stockCode={}", stockCode);

        String cacheKey = REDIS_KEY_AI_REPORT_PREFIX + stockCode;

        // 1. Redis 캐시 조회
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("[getAiReport] Cache HIT for stockCode={}", stockCode);
                if (cached instanceof LinkedHashMap) {
                    return convertToAiReportDto((LinkedHashMap<String, Object>) cached);
                }
            }
        } catch (Exception e) {
            log.warn("[getAiReport] Redis cache read failed for stockCode={}: {}", stockCode, e.getMessage());
        }

        log.info("[getAiReport] Cache MISS for stockCode={} - fetching from database", stockCode);

        // 2. DB에서 조회
        Optional<StockAnalysis> analysisOpt = stockAnalysisRepository
                .findFirstByStockCodeOrderByUpdatedAtDesc(stockCode);

        StockAnalysis analysis;
        if (analysisOpt.isPresent()) {
            analysis = analysisOpt.get();
        } else {
            // DB에 없으면 새로 생성
            log.info("[getAiReport] No analysis found for stockCode={} - generating new analysis", stockCode);
            analysis = generateAnalysisForStock(stockCode);
        }

        // 3. DTO 변환
        AiReportDto result = AiReportDto.from(analysis);

        // 4. Redis에 저장
        try {
            redisTemplate.opsForValue().set(cacheKey, result, CACHE_TTL_REPORT);
            log.info("[getAiReport] Cached AI report for stockCode={} with TTL {} minutes",
                    stockCode, CACHE_TTL_REPORT.toMinutes());
        } catch (Exception e) {
            log.warn("[getAiReport] Redis cache write failed for stockCode={}: {}", stockCode, e.getMessage());
        }

        return result;
    }

    /**
     * Top 10 캐시 강제 갱신 (스케줄러에서 호출)
     */
    @Scheduled(fixedRate = 600000) // 10분마다 실행
    @Transactional
    public void refreshTop10Cache() {
        log.info("[refreshTop10Cache] Starting scheduled Top 10 cache refresh");

        try {
            // 1. 기존 캐시 삭제
            redisTemplate.delete(REDIS_KEY_TOP10);

            // 2. 최신 데이터로 갱신
            updateTop10AnalysisData();

            // 3. 캐시 새로 로드
            List<TopStockDto> refreshed = getTop10Stocks();

            log.info("[refreshTop10Cache] Successfully refreshed Top 10 cache with {} stocks",
                    refreshed.size());
        } catch (Exception e) {
            log.error("[refreshTop10Cache] Failed to refresh Top 10 cache: {}", e.getMessage(), e);
        }
    }

    /**
     * Top 10 분석 데이터 업데이트
     */
    @Transactional
    public void updateTop10AnalysisData() {
        log.info("[updateTop10AnalysisData] Updating analysis data for Top 10 stocks");

        for (String stockCode : TOP_10_STOCK_CODES) {
            try {
                StockAnalysis analysis = stockAnalysisRepository
                        .findFirstByStockCodeOrderByUpdatedAtDesc(stockCode)
                        .orElseGet(() -> createNewAnalysis(stockCode));

                // 네이버에서 실시간 가격 정보 조회
                updateWithRealTimeData(analysis, stockCode);

                // AI 점수 및 분석 업데이트
                updateAiAnalysis(analysis);

                stockAnalysisRepository.save(analysis);
                log.debug("[updateTop10AnalysisData] Updated analysis for stockCode={}", stockCode);
            } catch (Exception e) {
                log.error("[updateTop10AnalysisData] Failed to update stockCode={}: {}",
                        stockCode, e.getMessage());
            }
        }

        log.info("[updateTop10AnalysisData] Completed updating {} stocks", TOP_10_STOCK_CODES.size());
    }

    /**
     * 초기 분석 데이터 생성
     */
    private List<StockAnalysis> generateInitialAnalysisData() {
        log.info("[generateInitialAnalysisData] Generating initial analysis data for Top 10 stocks");

        List<StockAnalysis> analyses = new ArrayList<>();

        for (String stockCode : TOP_10_STOCK_CODES) {
            StockAnalysis analysis = generateAnalysisForStock(stockCode);
            analyses.add(analysis);
        }

        return analyses;
    }

    /**
     * 특정 종목의 분석 데이터 생성
     */
    @Transactional
    public StockAnalysis generateAnalysisForStock(String stockCode) {
        log.info("[generateAnalysisForStock] Generating analysis for stockCode={}", stockCode);

        StockAnalysis analysis = createNewAnalysis(stockCode);
        updateWithRealTimeData(analysis, stockCode);
        updateAiAnalysis(analysis);

        return stockAnalysisRepository.save(analysis);
    }

    /**
     * 새 분석 엔티티 생성
     */
    private StockAnalysis createNewAnalysis(String stockCode) {
        return StockAnalysis.builder()
                .stockCode(stockCode)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 실시간 데이터로 업데이트 (시가총액 포함)
     */
    private void updateWithRealTimeData(StockAnalysis analysis, String stockCode) {
        try {
            var stockData = naverStockService.fetchStock(stockCode);
            if (stockData != null) {
                // 종목명
                analysis.setStockName(stockData.get("name") != null ?
                        stockData.get("name").toString() : getStockNameByCode(stockCode));

                // 현재가
                analysis.setCurrentPrice(stockData.get("price") != null ?
                        ((Number) stockData.get("price")).longValue() : null);

                // 등락률
                analysis.setChangePercent(stockData.get("changePercent") != null ?
                        ((Number) stockData.get("changePercent")).doubleValue() : null);

                // 시가총액 - 실시간 데이터 우선, 없으면 하드코딩 값 사용
                Long marketCap = null;
                if (stockData.get("marketCap") != null) {
                    marketCap = ((Number) stockData.get("marketCap")).longValue();
                }
                // 실시간 시가총액이 없거나 0이면 fallback 사용
                if (marketCap == null || marketCap == 0) {
                    marketCap = getMarketCapByCode(stockCode);
                    log.debug("[updateWithRealTimeData] Using fallback marketCap for {}: {}", stockCode, marketCap);
                } else {
                    log.debug("[updateWithRealTimeData] Using real-time marketCap for {}: {}", stockCode, marketCap);
                }
                analysis.setMarketCap(marketCap);
            } else {
                // API 실패 시 기본값 설정
                analysis.setStockName(getStockNameByCode(stockCode));
                analysis.setMarketCap(getMarketCapByCode(stockCode));
            }
        } catch (Exception e) {
            log.warn("[updateWithRealTimeData] Failed to fetch real-time data for stockCode={}: {}",
                    stockCode, e.getMessage());
            // 기본값 설정
            analysis.setStockName(getStockNameByCode(stockCode));
            analysis.setMarketCap(getMarketCapByCode(stockCode));
        }
    }

    /**
     * AI 분석 업데이트 (Mock 데이터 - 실제 AI 모델 연동 시 교체)
     */
    private void updateAiAnalysis(StockAnalysis analysis) {
        Random random = new Random();

        // 기술적 분석 점수 (Mock)
        int technicalScore = 40 + random.nextInt(40); // 40~80
        analysis.setTechnicalScore(technicalScore);

        // 감성 분석 점수 (Mock)
        int sentimentScore = 35 + random.nextInt(45); // 35~80
        analysis.setSentimentScore(sentimentScore);

        // 종합 점수 계산
        analysis.calculateTotalScore();

        // 매매 신호 결정
        analysis.determineSignalType();

        // AI 한줄평 생성
        analysis.setSummary(generateSummary(analysis));

        // SWOT 분석 등 상세 리포트 생성
        analysis.setReportJson(generateReportJson(analysis));
    }

    /**
     * AI 한줄평 생성 (Mock)
     */
    private String generateSummary(StockAnalysis analysis) {
        String[] positiveSummaries = {
                "외국인 매수세 유입으로 상승 모멘텀 강화",
                "실적 개선 기대감으로 긍정적 전망",
                "기술적 지표 상승 신호 포착",
                "업종 대비 상대적 강세 지속",
                "주요 지지선 확인으로 반등 기대"
        };

        String[] neutralSummaries = {
                "박스권 횡보 지속, 방향성 확인 필요",
                "거래량 감소로 관망세 우세",
                "혼조세 지속, 신중한 접근 권고",
                "단기 조정 후 방향성 탐색 중"
        };

        String[] negativeSummaries = {
                "수급 악화로 하락 압력 증가",
                "기술적 지표 과매도 구간 진입",
                "외국인 매도세 지속으로 약세",
                "실적 우려로 투자심리 위축"
        };

        int score = analysis.getTotalScore() != null ? analysis.getTotalScore() : 50;
        Random random = new Random();

        if (score >= 65) {
            return positiveSummaries[random.nextInt(positiveSummaries.length)];
        } else if (score >= 45) {
            return neutralSummaries[random.nextInt(neutralSummaries.length)];
        } else {
            return negativeSummaries[random.nextInt(negativeSummaries.length)];
        }
    }

    /**
     * SWOT 분석 등 상세 리포트 JSON 생성 (Mock)
     */
    private String generateReportJson(StockAnalysis analysis) {
        try {
            Map<String, Object> report = new HashMap<>();

            // SWOT 분석
            Map<String, List<String>> swot = new HashMap<>();
            swot.put("strengths", generateSwotItems("strength", analysis.getStockCode()));
            swot.put("weaknesses", generateSwotItems("weakness", analysis.getStockCode()));
            swot.put("opportunities", generateSwotItems("opportunity", analysis.getStockCode()));
            swot.put("threats", generateSwotItems("threat", analysis.getStockCode()));
            report.put("swot", swot);

            // 기술적 지표
            Map<String, Object> technical = new HashMap<>();
            technical.put("trend", getTrendByScore(analysis.getTechnicalScore()));
            technical.put("rsiValue", 30 + new Random().nextInt(40));
            technical.put("rsiSignal", getRsiSignal((int) technical.get("rsiValue")));
            technical.put("macdValue", (new Random().nextDouble() - 0.5) * 100);
            technical.put("macdSignal", analysis.getTechnicalScore() > 50 ? "BUY" : "SELL");
            technical.put("movingAverage", analysis.getTechnicalScore() > 50 ? "ABOVE_MA" : "BELOW_MA");
            technical.put("volumeChange", (new Random().nextDouble() - 0.3) * 50);
            report.put("technicalIndicators", technical);

            // 감성 분석 데이터
            Map<String, Object> sentiment = new HashMap<>();
            int positiveCount = new Random().nextInt(10);
            int negativeCount = new Random().nextInt(5);
            sentiment.put("positiveNewsCount", positiveCount);
            sentiment.put("negativeNewsCount", negativeCount);
            sentiment.put("neutralNewsCount", new Random().nextInt(8));
            sentiment.put("overallSentiment", (positiveCount - negativeCount) / 10.0);
            sentiment.put("recentHeadlines", generateRecentHeadlines(analysis.getStockName()));
            report.put("sentimentData", sentiment);

            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            log.error("[generateReportJson] Failed to generate report JSON: {}", e.getMessage());
            return "{}";
        }
    }

    private List<String> generateSwotItems(String type, String stockCode) {
        Map<String, List<String>> swotTemplates = new HashMap<>();

        swotTemplates.put("strength", Arrays.asList(
                "글로벌 시장 점유율 1위",
                "안정적인 현금 흐름",
                "높은 브랜드 가치",
                "R&D 투자 지속 확대"
        ));
        swotTemplates.put("weakness", Arrays.asList(
                "원자재 가격 변동 리스크",
                "환율 변동에 민감",
                "경쟁 심화로 마진 압박"
        ));
        swotTemplates.put("opportunity", Arrays.asList(
                "신규 시장 진출 기회",
                "정부 정책 수혜 기대",
                "친환경 전환 수요 증가"
        ));
        swotTemplates.put("threat", Arrays.asList(
                "글로벌 경기 침체 우려",
                "규제 강화 가능성",
                "신규 경쟁자 진입"
        ));

        List<String> templates = swotTemplates.getOrDefault(type, Collections.emptyList());
        Random random = new Random(stockCode.hashCode() + type.hashCode());
        int count = 2 + random.nextInt(2);

        return templates.stream()
                .limit(count)
                .collect(Collectors.toList());
    }

    private String getTrendByScore(Integer score) {
        if (score == null) return "SIDEWAYS";
        if (score >= 60) return "UPTREND";
        if (score <= 40) return "DOWNTREND";
        return "SIDEWAYS";
    }

    private String getRsiSignal(int rsiValue) {
        if (rsiValue >= 70) return "OVERBOUGHT";
        if (rsiValue <= 30) return "OVERSOLD";
        return "NEUTRAL";
    }

    private List<String> generateRecentHeadlines(String stockName) {
        if (stockName == null) stockName = "해당 기업";
        return Arrays.asList(
                stockName + ", 분기 실적 시장 예상치 상회",
                stockName + " 신규 사업 확장 계획 발표",
                "전문가들 \"" + stockName + " 목표가 상향 조정\""
        );
    }

    /**
     * 종목 코드로 종목명 조회 (하드코딩된 매핑)
     */
    private String getStockNameByCode(String stockCode) {
        Map<String, String> stockNames = new HashMap<>();
        stockNames.put("005930", "삼성전자");
        stockNames.put("000660", "SK하이닉스");
        stockNames.put("373220", "LG에너지솔루션");
        stockNames.put("207940", "삼성바이오로직스");
        stockNames.put("005380", "현대차");
        stockNames.put("000270", "기아");
        stockNames.put("068270", "셀트리온");
        stockNames.put("035420", "NAVER");
        stockNames.put("005490", "POSCO홀딩스");
        stockNames.put("035720", "카카오");
        return stockNames.getOrDefault(stockCode, "알 수 없음");
    }

    /**
     * 종목 코드로 시가총액 조회 (fallback 값, 실시간 API 실패 시 사용)
     * 주의: 이 값은 fallback용이며, 실제 시가총액은 네이버 API에서 가져옴
     */
    private Long getMarketCapByCode(String stockCode) {
        Map<String, Long> marketCaps = new HashMap<>();
        // 2026년 2월 기준 대략적인 시가총액 (fallback용)
        marketCaps.put("005930", 890_000_000_000_000L);  // 삼성전자 ~890조
        marketCaps.put("000660", 170_000_000_000_000L);  // SK하이닉스 ~170조
        marketCaps.put("373220", 95_000_000_000_000L);   // LG에너지솔루션 ~95조
        marketCaps.put("207940", 55_000_000_000_000L);   // 삼성바이오 ~55조
        marketCaps.put("005380", 50_000_000_000_000L);   // 현대차 ~50조
        marketCaps.put("000270", 42_000_000_000_000L);   // 기아 ~42조
        marketCaps.put("068270", 38_000_000_000_000L);   // 셀트리온 ~38조
        marketCaps.put("035420", 32_000_000_000_000L);   // NAVER ~32조
        marketCaps.put("005490", 28_000_000_000_000L);   // POSCO홀딩스 ~28조
        marketCaps.put("035720", 20_000_000_000_000L);   // 카카오 ~20조
        return marketCaps.getOrDefault(stockCode, 10_000_000_000_000L);
    }

    /**
     * LinkedHashMap을 TopStockDto 리스트로 변환
     */
    @SuppressWarnings("unchecked")
    private List<TopStockDto> convertToTopStockDtoList(List<LinkedHashMap<String, Object>> cachedList) {
        return cachedList.stream()
                .map(map -> TopStockDto.builder()
                        .rank(map.get("rank") != null ? ((Number) map.get("rank")).intValue() : null)
                        .stockCode((String) map.get("stockCode"))
                        .stockName((String) map.get("stockName"))
                        .currentPrice(map.get("currentPrice") != null ?
                                ((Number) map.get("currentPrice")).longValue() : null)
                        .changePercent(map.get("changePercent") != null ?
                                ((Number) map.get("changePercent")).doubleValue() : null)
                        .marketCap(map.get("marketCap") != null ?
                                ((Number) map.get("marketCap")).longValue() : null)
                        .aiScore(map.get("aiScore") != null ?
                                ((Number) map.get("aiScore")).intValue() : null)
                        .summary((String) map.get("summary"))
                        .signalType((String) map.get("signalType"))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * LinkedHashMap을 AiReportDto로 변환
     */
    @SuppressWarnings("unchecked")
    private AiReportDto convertToAiReportDto(LinkedHashMap<String, Object> map) {
        AiReportDto.AiReportDtoBuilder builder = AiReportDto.builder()
                .stockCode((String) map.get("stockCode"))
                .stockName((String) map.get("stockName"))
                .currentPrice(map.get("currentPrice") != null ?
                        ((Number) map.get("currentPrice")).longValue() : null)
                .changePercent(map.get("changePercent") != null ?
                        ((Number) map.get("changePercent")).doubleValue() : null)
                .marketCap(map.get("marketCap") != null ?
                        ((Number) map.get("marketCap")).longValue() : null)
                .totalScore(map.get("totalScore") != null ?
                        ((Number) map.get("totalScore")).intValue() : null)
                .technicalScore(map.get("technicalScore") != null ?
                        ((Number) map.get("technicalScore")).intValue() : null)
                .sentimentScore(map.get("sentimentScore") != null ?
                        ((Number) map.get("sentimentScore")).intValue() : null)
                .signalType((String) map.get("signalType"))
                .summary((String) map.get("summary"));

        // SWOT 파싱
        if (map.get("swot") instanceof LinkedHashMap) {
            LinkedHashMap<String, Object> swotMap = (LinkedHashMap<String, Object>) map.get("swot");
            builder.swot(AiReportDto.SwotAnalysis.builder()
                    .strengths((List<String>) swotMap.get("strengths"))
                    .weaknesses((List<String>) swotMap.get("weaknesses"))
                    .opportunities((List<String>) swotMap.get("opportunities"))
                    .threats((List<String>) swotMap.get("threats"))
                    .build());
        }

        return builder.build();
    }

    /**
     * 캐시 무효화
     */
    public void invalidateCache(String stockCode) {
        log.info("[invalidateCache] Invalidating cache for stockCode={}", stockCode);
        try {
            redisTemplate.delete(REDIS_KEY_AI_REPORT_PREFIX + stockCode);
            redisTemplate.delete(REDIS_KEY_TOP10);
        } catch (Exception e) {
            log.warn("[invalidateCache] Failed to invalidate cache: {}", e.getMessage());
        }
    }
}
