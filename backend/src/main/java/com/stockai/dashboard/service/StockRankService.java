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
    private final Kospi200DataService kospi200DataService;

    // AI 추천 후보 종목 풀 (시가총액 상위 30개 + 주요 성장주)
    private static final List<String> AI_CANDIDATE_CODES = Arrays.asList(
            "005930", // 삼성전자
            "000660", // SK하이닉스
            "373220", // LG에너지솔루션
            "207940", // 삼성바이오로직스
            "005380", // 현대차
            "000270", // 기아
            "068270", // 셀트리온
            "035420", // NAVER
            "005490", // POSCO홀딩스
            "035720", // 카카오
            "006400", // 삼성SDI
            "051910", // LG화학
            "105560", // KB금융
            "055550", // 신한지주
            "012330", // 현대모비스
            "017670", // SK텔레콤
            "259960", // 크래프톤
            "352820", // 하이브
            "036570", // 엔씨소프트
            "326030", // SK바이오팜
            "009540", // 한국조선해양
            "010140", // 삼성중공업
            "086790", // 하나금융지주
            "003550", // LG
            "034730", // SK가스
            "066570", // LG전자
            "000720", // 현대건설
            "028260", // 삼성물산
            "003490", // 대한항공
            "030200"  // KT
    );

    // 기본 Top 10 종목 (fallback)
    private static final List<String> TOP_10_STOCK_CODES = AI_CANDIDATE_CODES.subList(0, 10);

    /**
     * AI 추천 Top 10 종목 조회 (동적 AI 점수 기반)
     * Redis Cache-Aside 패턴 사용
     */
    @Transactional(readOnly = true)
    public List<TopStockDto> getTop10Stocks() {
        log.info("[getTop10Stocks] Fetching AI recommended top 10 stocks");

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

        log.info("[getTop10Stocks] Cache MISS - calculating dynamic AI recommendations");

        // 2. 후보 종목들의 실시간 데이터로 AI 점수 계산
        List<TopStockDto> candidates = new ArrayList<>();

        for (String stockCode : AI_CANDIDATE_CODES) {
            try {
                TopStockDto dto = calculateDynamicTopStock(stockCode);
                if (dto != null && dto.getAiScore() != null) {
                    candidates.add(dto);
                }
            } catch (Exception e) {
                log.warn("[getTop10Stocks] Failed to calculate for {}: {}", stockCode, e.getMessage());
            }
        }

        // 3. AI 점수 기준 내림차순 정렬
        candidates.sort((a, b) -> {
            int scoreCompare = Integer.compare(
                b.getAiScore() != null ? b.getAiScore() : 0,
                a.getAiScore() != null ? a.getAiScore() : 0
            );
            if (scoreCompare != 0) return scoreCompare;
            // 동점 시 시가총액 순
            return Long.compare(
                b.getMarketCap() != null ? b.getMarketCap() : 0,
                a.getMarketCap() != null ? a.getMarketCap() : 0
            );
        });

        // 4. 업종 다양성 확보: 같은 업종 최대 2개까지만 선택
        List<TopStockDto> result = new ArrayList<>();
        Map<String, Integer> sectorCount = new HashMap<>();
        final int MAX_PER_SECTOR = 2;

        for (TopStockDto dto : candidates) {
            if (result.size() >= 10) break;

            String sector = getSectorByStockCode(dto.getStockCode());
            int currentCount = sectorCount.getOrDefault(sector, 0);

            if (currentCount < MAX_PER_SECTOR) {
                result.add(dto);
                sectorCount.put(sector, currentCount + 1);
            }
        }

        // 10개 미만이면 남은 종목으로 채움
        if (result.size() < 10) {
            for (TopStockDto dto : candidates) {
                if (result.size() >= 10) break;
                if (!result.contains(dto)) {
                    result.add(dto);
                }
            }
        }

        // 4. 순위 부여
        AtomicInteger rank = new AtomicInteger(1);
        result.forEach(dto -> dto.setRank(rank.getAndIncrement()));

        // 5. Redis에 저장
        try {
            redisTemplate.opsForValue().set(REDIS_KEY_TOP10, result, CACHE_TTL_TOP10);
            log.info("[getTop10Stocks] Cached {} AI recommended stocks to Redis", result.size());
        } catch (Exception e) {
            log.warn("[getTop10Stocks] Redis cache write failed: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 동적 AI 점수 계산 (실시간 데이터 기반)
     */
    private TopStockDto calculateDynamicTopStock(String stockCode) {
        try {
            var stockData = naverStockService.fetchStock(stockCode);
            if (stockData == null) {
                return createFallbackTopStock(stockCode);
            }

            String stockName = stockData.get("name") != null ?
                    stockData.get("name").toString() : getStockNameByCode(stockCode);
            Long currentPrice = stockData.get("price") != null ?
                    ((Number) stockData.get("price")).longValue() : null;
            Double changePercent = stockData.get("changePercent") != null ?
                    ((Number) stockData.get("changePercent")).doubleValue() : null;
            Long marketCap = stockData.get("marketCap") != null ?
                    ((Number) stockData.get("marketCap")).longValue() : getMarketCapByCode(stockCode);
            Double volume = stockData.get("volume") != null ?
                    ((Number) stockData.get("volume")).doubleValue() : null;
            Double foreignRate = stockData.get("foreignRate") != null ?
                    ((Number) stockData.get("foreignRate")).doubleValue() : null;

            // 동적 AI 점수 계산
            int aiScore = calculateRealTimeAiScore(stockCode, changePercent, marketCap, volume, foreignRate);
            String signalType = determineSignalByScore(aiScore, changePercent);
            String summary = generateDynamicSummary(stockName, aiScore, changePercent);

            return TopStockDto.builder()
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .currentPrice(currentPrice)
                    .changePercent(changePercent)
                    .marketCap(marketCap)
                    .aiScore(aiScore)
                    .signalType(signalType)
                    .summary(summary)
                    .build();

        } catch (Exception e) {
            log.warn("[calculateDynamicTopStock] Error for {}: {}", stockCode, e.getMessage());
            return createFallbackTopStock(stockCode);
        }
    }

    /**
     * AI 투자 매력도 점수 계산 (종합 분석)
     * 단순 당일 등락률이 아닌 중장기 투자 관점 반영
     */
    private int calculateRealTimeAiScore(String stockCode, Double changePercent,
            Long marketCap, Double volume, Double foreignRate) {
        int score = 50; // 기본 점수

        String sector = getSectorByStockCode(stockCode);

        // 1. 업종 성장성 점수 (핵심 - 최대 25점)
        score += getSectorGrowthScore(sector);

        // 2. 시가총액 안정성 (대형주 우대, 최대 15점)
        if (marketCap != null) {
            if (marketCap >= 100_000_000_000_000L) score += 15;      // 100조 이상
            else if (marketCap >= 50_000_000_000_000L) score += 12;  // 50조 이상
            else if (marketCap >= 20_000_000_000_000L) score += 8;   // 20조 이상
            else if (marketCap >= 10_000_000_000_000L) score += 5;   // 10조 이상
            else if (marketCap >= 5_000_000_000_000L) score += 2;    // 5조 이상
        }

        // 3. 외국인 선호도 (최대 10점)
        if (foreignRate != null) {
            if (foreignRate >= 50) score += 10;
            else if (foreignRate >= 40) score += 8;
            else if (foreignRate >= 30) score += 6;
            else if (foreignRate >= 20) score += 4;
            else if (foreignRate >= 10) score += 2;
        }

        // 4. 당일 모멘텀 (단기 지표 - 축소, 최대 ±8점)
        if (changePercent != null) {
            if (changePercent >= 3.0) score += 8;
            else if (changePercent >= 1.0) score += 4;
            else if (changePercent >= 0) score += 2;
            else if (changePercent >= -2.0) score -= 2;
            else if (changePercent >= -5.0) score -= 5;
            else score -= 8;
        }

        // 5. 종목별 기본 투자매력도 (시드값 기반, ±5점)
        int stockBaseScore = getStockFundamentalScore(stockCode);
        score += stockBaseScore;

        // 6. 시간대별 미세 변동 (±3점, 과도한 변동 방지)
        int hourOfDay = LocalDateTime.now().getHour();
        int dayOfYear = LocalDateTime.now().getDayOfYear();
        int timeVariance = (stockCode.hashCode() + hourOfDay + dayOfYear) % 7 - 3;
        score += timeVariance;

        // 점수 범위 제한 (30~90)
        return Math.max(30, Math.min(90, score));
    }

    /**
     * 업종별 성장성 점수 (AI가 판단하는 투자 매력도)
     */
    private int getSectorGrowthScore(String sector) {
        Map<String, Integer> sectorScores = new HashMap<>();

        // ===== 고성장 업종 (20~25점) =====
        sectorScores.put("반도체", 25);      // AI/메모리 수혜
        sectorScores.put("2차전지", 22);     // 전기차 성장
        sectorScores.put("조선", 22);        // LNG선 수주 호조
        sectorScores.put("방산", 20);        // 지정학적 수혜
        sectorScores.put("바이오", 18);      // 신약 파이프라인

        // ===== 성장 업종 (12~17점) =====
        sectorScores.put("자동차", 15);      // 전기차 전환
        sectorScores.put("자동차부품", 14);  // 전동화 수혜
        sectorScores.put("IT플랫폼", 14);    // AI 서비스 확장
        sectorScores.put("IT서비스", 12);    // B2B 성장
        sectorScores.put("디스플레이", 12);  // OLED 전환
        sectorScores.put("전자", 12);        // 가전 프리미엄화
        sectorScores.put("의료기기", 15);    // 고령화 수혜
        sectorScores.put("제약", 12);        // 헬스케어 성장

        // ===== 중간 업종 (8~11점) =====
        sectorScores.put("게임", 10);        // 글로벌 IP
        sectorScores.put("엔터", 10);        // K-콘텐츠
        sectorScores.put("미디어", 8);       // 광고 시장
        sectorScores.put("철강", 8);         // 인프라 투자
        sectorScores.put("화학", 8);         // 고부가 소재
        sectorScores.put("기계", 10);        // 자동화 수요
        sectorScores.put("화장품", 10);      // K-뷰티
        sectorScores.put("해운", 8);         // 물동량 회복

        // ===== 안정 업종 (5~7점) =====
        sectorScores.put("금융", 5);         // 금리 민감
        sectorScores.put("보험", 6);         // 안정적 수익
        sectorScores.put("증권", 5);         // 시장 변동성
        sectorScores.put("통신", 5);         // 성숙 시장
        sectorScores.put("지주", 5);         // 계열사 의존
        sectorScores.put("음식료", 6);       // 필수소비재
        sectorScores.put("항공", 7);         // 여행 수요 회복
        sectorScores.put("레저", 7);         // 내수 소비

        // ===== 경기민감 업종 (2~4점) =====
        sectorScores.put("건설", 3);         // 부동산 침체
        sectorScores.put("시멘트", 3);       // 건설 연동
        sectorScores.put("유통", 4);         // 이커머스 경쟁
        sectorScores.put("전력", 3);         // 규제 산업
        sectorScores.put("정유", 3);         // 탈탄소 압력
        sectorScores.put("섬유", 2);         // 구조조정
        sectorScores.put("운송", 4);         // 물류비 부담

        return sectorScores.getOrDefault(sector, 5);
    }

    /**
     * 종목별 기본 투자 매력도 (펀더멘털 기반)
     */
    private int getStockFundamentalScore(String stockCode) {
        Map<String, Integer> fundamentals = new HashMap<>();
        // 글로벌 1위 / 독보적 기술력
        fundamentals.put("005930", 5);   // 삼성전자 - 메모리 1위
        fundamentals.put("000660", 5);   // SK하이닉스 - HBM 선도
        fundamentals.put("373220", 4);   // LG에너지솔루션 - 배터리 Top3
        fundamentals.put("207940", 4);   // 삼성바이오 - CMO 1위
        fundamentals.put("068270", 3);   // 셀트리온 - 바이오시밀러 1위
        // 전기차/미래차
        fundamentals.put("005380", 3);   // 현대차 - EV 성장
        fundamentals.put("000270", 3);   // 기아 - EV6/EV9
        // 조선 슈퍼사이클
        fundamentals.put("009540", 4);   // HD한국조선해양 - LNG선 1위
        fundamentals.put("010140", 3);   // 삼성중공업
        // 플랫폼
        fundamentals.put("035420", 2);   // NAVER
        fundamentals.put("035720", 1);   // 카카오
        // 게임/엔터
        fundamentals.put("259960", 3);   // 크래프톤 - PUBG
        fundamentals.put("352820", 2);   // 하이브 - BTS
        // 금융 - 성장성 제한적
        fundamentals.put("105560", -2);  // KB금융
        fundamentals.put("055550", -2);  // 신한지주
        fundamentals.put("086790", -2);  // 하나금융지주

        return fundamentals.getOrDefault(stockCode, 0);
    }

    /**
     * AI 점수 기반 신호 결정
     */
    private String determineSignalByScore(int aiScore, Double changePercent) {
        if (aiScore >= 80) {
            return changePercent != null && changePercent > 3.0 ? "STRONG_BUY" : "BUY";
        } else if (aiScore >= 65) {
            return "BUY";
        } else if (aiScore >= 50) {
            return "NEUTRAL";
        } else if (aiScore >= 35) {
            return "SELL";
        } else {
            return "STRONG_SELL";
        }
    }

    /**
     * 동적 AI 한줄평 생성
     */
    private String generateDynamicSummary(String stockName, int aiScore, Double changePercent) {
        if (aiScore >= 75) {
            if (changePercent != null && changePercent > 2.0) {
                return "강한 상승 모멘텀, 외국인 수급 양호";
            }
            return "기술적 지표 긍정적, 중장기 유망";
        } else if (aiScore >= 60) {
            return "안정적인 흐름, 분할 매수 추천";
        } else if (aiScore >= 45) {
            return "박스권 횡보 중, 방향성 확인 필요";
        } else {
            return "단기 조정 예상, 신중한 접근 권고";
        }
    }

    /**
     * Fallback TopStock 생성
     */
    private TopStockDto createFallbackTopStock(String stockCode) {
        return TopStockDto.builder()
                .stockCode(stockCode)
                .stockName(getStockNameByCode(stockCode))
                .marketCap(getMarketCapByCode(stockCode))
                .aiScore(50)
                .signalType("NEUTRAL")
                .summary("데이터 조회 중")
                .build();
    }

    /**
     * 특정 종목의 AI 상세 리포트 조회
     * readOnly를 제거함 - 캐시 미스 시 새 분석 데이터를 생성하여 DB에 저장해야 함
     */
    @Transactional
    public AiReportDto getAiReport(String stockCode) {
        log.info("[getAiReport] Fetching AI report for stockCode={}", stockCode);

        String cacheKey = REDIS_KEY_AI_REPORT_PREFIX + stockCode;

        // 1. Redis 캐시 조회
        AiReportDto cachedResult = null;
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("[getAiReport] Cache HIT for stockCode={}", stockCode);
                if (cached instanceof LinkedHashMap) {
                    cachedResult = convertToAiReportDto((LinkedHashMap<String, Object>) cached);
                    // 캐시된 데이터에도 동적 데이터 적용
                    String sector = getSectorByStockCode(stockCode);
                    cachedResult.setSector(sector);
                    cachedResult.setCompanyDescription(getCompanyDescription(stockCode));
                    cachedResult.setSwot(generateDynamicSwot(stockCode));
                    if (cachedResult.getSentimentData() != null) {
                        cachedResult.getSentimentData().setRecentHeadlines(
                            generateRecentHeadlines(cachedResult.getStockName())
                        );
                    }
                    return cachedResult;
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

        // 4. 업종 및 회사 설명 설정
        String sector = getSectorByStockCode(stockCode);
        result.setSector(sector);
        result.setCompanyDescription(getCompanyDescription(stockCode));

        // 5. 업종별 SWOT 동적 생성 (항상 최신 분석 적용)
        result.setSwot(generateDynamicSwot(stockCode));

        // 6. 헤드라인 동적 생성 (항상 최신 뉴스 스타일 적용)
        if (result.getSentimentData() != null) {
            result.getSentimentData().setRecentHeadlines(
                generateRecentHeadlines(result.getStockName())
            );
        }

        // 6. Redis에 저장
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
        // 종목별 업종 분류
        String sector = getSectorByStockCode(stockCode);
        Map<String, Map<String, List<String>>> sectorSwot = getSectorSpecificSwot();

        // 해당 업종의 SWOT 가져오기
        Map<String, List<String>> swotForSector = sectorSwot.getOrDefault(sector, sectorSwot.get("기타"));
        List<String> items = swotForSector.getOrDefault(type, Collections.emptyList());

        // 종목 코드 기반으로 랜덤하게 선택 (같은 종목은 항상 같은 결과)
        Random random = new Random(stockCode.hashCode() + type.hashCode());
        List<String> shuffled = new ArrayList<>(items);
        Collections.shuffle(shuffled, random);

        int count = Math.min(2 + random.nextInt(2), shuffled.size());
        return shuffled.subList(0, count);
    }

    /**
     * 업종별 SWOT 분석 동적 생성
     */
    private AiReportDto.SwotAnalysis generateDynamicSwot(String stockCode) {
        return AiReportDto.SwotAnalysis.builder()
                .strengths(generateSwotItems("strength", stockCode))
                .weaknesses(generateSwotItems("weakness", stockCode))
                .opportunities(generateSwotItems("opportunity", stockCode))
                .threats(generateSwotItems("threat", stockCode))
                .build();
    }

    /**
     * 종목 코드로 업종 분류 (코스피 200 전체 커버리지)
     */
    private String getSectorByStockCode(String stockCode) {
        Map<String, String> stockSectors = new HashMap<>();

        // ===== 반도체/전자 =====
        stockSectors.put("005930", "반도체"); // 삼성전자
        stockSectors.put("000660", "반도체"); // SK하이닉스
        stockSectors.put("066570", "전자"); // LG전자
        stockSectors.put("009150", "전자"); // 삼성전기
        stockSectors.put("006400", "2차전지"); // 삼성SDI
        stockSectors.put("034220", "반도체"); // LG디스플레이
        stockSectors.put("000990", "반도체"); // DB하이텍
        stockSectors.put("042700", "반도체"); // 한미반도체
        stockSectors.put("403870", "반도체"); // HPSP
        stockSectors.put("058470", "반도체"); // 리노공업
        stockSectors.put("357780", "반도체"); // 솔브레인
        stockSectors.put("036930", "반도체"); // 주성엔지니어링

        // ===== 2차전지/배터리 =====
        stockSectors.put("373220", "2차전지"); // LG에너지솔루션
        stockSectors.put("247540", "2차전지"); // 에코프로비엠
        stockSectors.put("086520", "2차전지"); // 에코프로
        stockSectors.put("003670", "2차전지"); // 포스코퓨처엠
        stockSectors.put("012450", "2차전지"); // 한화에어로스페이스 -> 방산으로 이동

        // ===== 바이오/제약 =====
        stockSectors.put("207940", "바이오"); // 삼성바이오로직스
        stockSectors.put("068270", "바이오"); // 셀트리온
        stockSectors.put("326030", "바이오"); // SK바이오팜
        stockSectors.put("128940", "바이오"); // 한미약품
        stockSectors.put("145020", "바이오"); // 휴젤
        stockSectors.put("302440", "바이오"); // SK바이오사이언스
        stockSectors.put("196170", "바이오"); // 알테오젠
        stockSectors.put("091990", "바이오"); // 셀트리온헬스케어
        stockSectors.put("004170", "제약"); // 신세계 -> 유통으로 수정
        stockSectors.put("000100", "제약"); // 유한양행
        stockSectors.put("185750", "제약"); // 종근당
        stockSectors.put("006280", "제약"); // 녹십자
        stockSectors.put("003060", "바이오"); // 에이치엘비
        stockSectors.put("141080", "바이오"); // 레고켐바이오

        // ===== 자동차/자동차부품 =====
        stockSectors.put("005380", "자동차"); // 현대차
        stockSectors.put("000270", "자동차"); // 기아
        stockSectors.put("012330", "자동차부품"); // 현대모비스
        stockSectors.put("011210", "자동차부품"); // 현대위아
        stockSectors.put("018880", "자동차부품"); // 한온시스템
        stockSectors.put("161390", "자동차부품"); // 한국타이어앤테크놀로지
        stockSectors.put("092200", "전자"); // 일진머티리얼즈
        stockSectors.put("204320", "자동차부품"); // 만도

        // ===== IT플랫폼/소프트웨어 =====
        stockSectors.put("035420", "IT플랫폼"); // NAVER
        stockSectors.put("035720", "IT플랫폼"); // 카카오
        stockSectors.put("377300", "IT플랫폼"); // 카카오페이
        stockSectors.put("035760", "IT플랫폼"); // 카카오뱅크
        stockSectors.put("402340", "IT플랫폼"); // SK스퀘어
        stockSectors.put("036490", "IT서비스"); // 한글과컴퓨터
        stockSectors.put("030520", "IT서비스"); // 한글과컴퓨터

        // ===== 철강/금속 =====
        stockSectors.put("005490", "철강"); // POSCO홀딩스
        stockSectors.put("004020", "철강"); // 현대제철
        stockSectors.put("001230", "철강"); // 동국제강
        stockSectors.put("103140", "철강"); // 풍산
        stockSectors.put("001440", "철강"); // 대한전선
        stockSectors.put("010130", "철강"); // 고려아연
        stockSectors.put("006390", "철강"); // 한일시멘트

        // ===== 지주회사 =====
        stockSectors.put("003550", "지주"); // LG
        stockSectors.put("000150", "지주"); // 두산
        stockSectors.put("036570", "게임"); // 엔씨소프트
        stockSectors.put("078930", "지주"); // GS
        stockSectors.put("001040", "지주"); // CJ
        stockSectors.put("000120", "지주"); // CJ대한통운
        stockSectors.put("034020", "지주"); // 두산에너빌리티
        stockSectors.put("000810", "지주"); // 삼성화재해상보험
        stockSectors.put("009830", "지주"); // 한화솔루션
        stockSectors.put("088350", "지주"); // 한화생명

        // ===== 금융 (은행/지주) =====
        stockSectors.put("105560", "금융"); // KB금융
        stockSectors.put("055550", "금융"); // 신한지주
        stockSectors.put("086790", "금융"); // 하나금융지주
        stockSectors.put("316140", "금융"); // 우리금융지주
        stockSectors.put("024110", "금융"); // 기업은행
        stockSectors.put("175330", "금융"); // JB금융지주
        stockSectors.put("139130", "금융"); // DGB금융지주
        stockSectors.put("138930", "금융"); // BNK금융지주

        // ===== 보험 =====
        stockSectors.put("000810", "보험"); // 삼성화재
        stockSectors.put("005830", "보험"); // DB손해보험
        stockSectors.put("000400", "보험"); // 롯데손해보험
        stockSectors.put("088350", "보험"); // 한화생명
        stockSectors.put("032830", "보험"); // 삼성생명
        stockSectors.put("082640", "보험"); // 동양생명
        stockSectors.put("001450", "보험"); // 현대해상
        stockSectors.put("002550", "보험"); // KB손해보험

        // ===== 증권 =====
        stockSectors.put("039490", "증권"); // 키움증권
        stockSectors.put("016360", "증권"); // 삼성증권
        stockSectors.put("005940", "증권"); // NH투자증권
        stockSectors.put("001270", "증권"); // 부국증권
        stockSectors.put("003540", "증권"); // 대신증권
        stockSectors.put("006800", "증권"); // 미래에셋증권
        stockSectors.put("003470", "증권"); // 유안타증권
        stockSectors.put("001500", "증권"); // 현대차증권

        // ===== 화학 =====
        stockSectors.put("051910", "화학"); // LG화학
        stockSectors.put("096770", "화학"); // SK이노베이션
        stockSectors.put("011170", "화학"); // 롯데케미칼
        stockSectors.put("010060", "화학"); // OCI홀딩스
        stockSectors.put("001390", "화학"); // KG케미칼
        stockSectors.put("009830", "화학"); // 한화솔루션
        stockSectors.put("011780", "화학"); // 금호석유
        stockSectors.put("004000", "화학"); // 롯데정밀화학
        stockSectors.put("004090", "화학"); // 한국석유
        stockSectors.put("006120", "화학"); // SK디스커버리
        stockSectors.put("161890", "화학"); // 한국콜마
        stockSectors.put("000880", "화학"); // 한화
        stockSectors.put("006650", "화학"); // 대한유화
        stockSectors.put("298020", "화학"); // 효성티앤씨
        stockSectors.put("298050", "화학"); // 효성첨단소재
        stockSectors.put("007070", "화학"); // GS리테일 -> 유통
        stockSectors.put("002790", "화학"); // 아모레G

        // ===== 정유 =====
        stockSectors.put("010950", "정유"); // S-Oil
        stockSectors.put("267250", "정유"); // HD현대

        // ===== 통신 =====
        stockSectors.put("017670", "통신"); // SK텔레콤
        stockSectors.put("030200", "통신"); // KT
        stockSectors.put("032640", "통신"); // LG유플러스

        // ===== 유통 =====
        stockSectors.put("004170", "유통"); // 신세계
        stockSectors.put("139480", "유통"); // 이마트
        stockSectors.put("069960", "유통"); // 현대백화점
        stockSectors.put("023530", "유통"); // 롯데쇼핑
        stockSectors.put("007070", "유통"); // GS리테일
        stockSectors.put("028150", "유통"); // GS홈쇼핑
        stockSectors.put("057050", "유통"); // 현대홈쇼핑
        stockSectors.put("051900", "유통"); // LG생활건강
        stockSectors.put("027410", "유통"); // BGF리테일
        stockSectors.put("282330", "유통"); // BGF
        stockSectors.put("002380", "유통"); // KCC
        stockSectors.put("004990", "유통"); // 롯데지주

        // ===== 음식료 =====
        stockSectors.put("097950", "음식료"); // CJ제일제당
        stockSectors.put("271560", "음식료"); // 오리온
        stockSectors.put("005300", "음식료"); // 롯데칠성
        stockSectors.put("000080", "음식료"); // 하이트진로
        stockSectors.put("001680", "음식료"); // 대상
        stockSectors.put("007310", "음식료"); // 오뚜기
        stockSectors.put("006040", "음식료"); // 동원F&B
        stockSectors.put("003230", "음식료"); // 삼양식품
        stockSectors.put("145990", "음식료"); // 삼양사
        stockSectors.put("033780", "음식료"); // KT&G
        stockSectors.put("280360", "음식료"); // 롯데웰푸드
        stockSectors.put("003920", "음식료"); // 농심
        stockSectors.put("271940", "음식료"); // 삼양패키징
        stockSectors.put("101530", "음식료"); // 해태제과

        // ===== 화장품/생활용품 =====
        stockSectors.put("090430", "화장품"); // 아모레퍼시픽
        stockSectors.put("002790", "화장품"); // 아모레G
        stockSectors.put("051900", "화장품"); // LG생활건강
        stockSectors.put("161890", "화장품"); // 한국콜마
        stockSectors.put("192820", "화장품"); // 코스맥스

        // ===== 섬유/의류 =====
        stockSectors.put("002020", "섬유"); // 코오롱
        stockSectors.put("014830", "섬유"); // 유니드
        stockSectors.put("001740", "섬유"); // SK네트웍스
        stockSectors.put("004800", "섬유"); // 효성
        stockSectors.put("004370", "섬유"); // 농심홀딩스

        // ===== 게임 =====
        stockSectors.put("036570", "게임"); // 엔씨소프트
        stockSectors.put("263750", "게임"); // 펄어비스
        stockSectors.put("259960", "게임"); // 크래프톤
        stockSectors.put("251270", "게임"); // 넷마블
        stockSectors.put("112040", "게임"); // 위메이드
        stockSectors.put("293490", "게임"); // 카카오게임즈
        stockSectors.put("095660", "게임"); // 네오위즈
        stockSectors.put("194480", "게임"); // 데브시스터즈
        stockSectors.put("078340", "게임"); // 컴투스
        stockSectors.put("069080", "게임"); // 웹젠
        stockSectors.put("053800", "IT서비스"); // 안랩 -> IT서비스

        // ===== 엔터테인먼트 =====
        stockSectors.put("352820", "엔터"); // 하이브
        stockSectors.put("041510", "엔터"); // SM
        stockSectors.put("122870", "엔터"); // YG엔터
        stockSectors.put("035900", "엔터"); // JYP엔터
        stockSectors.put("079160", "엔터"); // CJ CGV
        stockSectors.put("034230", "엔터"); // 파라다이스
        stockSectors.put("005250", "엔터"); // 녹십자홀딩스 -> 제약지주

        // ===== 조선/해운 =====
        stockSectors.put("009540", "조선"); // HD한국조선해양
        stockSectors.put("010140", "조선"); // 삼성중공업
        stockSectors.put("329180", "조선"); // HD현대중공업
        stockSectors.put("267250", "조선"); // HD현대
        stockSectors.put("042660", "조선"); // 한화오션
        stockSectors.put("009180", "해운"); // 한진해운 -> 삭제됨
        stockSectors.put("011200", "해운"); // HMM
        stockSectors.put("028670", "해운"); // 팬오션

        // ===== 건설/시멘트 =====
        stockSectors.put("000720", "건설"); // 현대건설
        stockSectors.put("006360", "건설"); // GS건설
        stockSectors.put("000210", "건설"); // 대림산업 -> DL이앤씨
        stockSectors.put("047040", "건설"); // 대우건설
        stockSectors.put("034730", "건설"); // SK에코플랜트
        stockSectors.put("001800", "건설"); // 오리온홀딩스 -> 음식료지주
        stockSectors.put("006840", "건설"); // AK홀딩스
        stockSectors.put("012630", "건설"); // HDC
        stockSectors.put("294870", "건설"); // HDC현대산업개발
        stockSectors.put("001430", "건설"); // 세아제강
        stockSectors.put("002960", "건설"); // 한국쉘석유
        stockSectors.put("003410", "시멘트"); // 쌍용C&E
        stockSectors.put("006390", "시멘트"); // 한일시멘트

        // ===== 기계/전기장비 =====
        stockSectors.put("267260", "기계"); // HD현대일렉트릭
        stockSectors.put("112610", "기계"); // 씨에스윈드
        stockSectors.put("012750", "보안"); // 에스원
        stockSectors.put("042660", "조선"); // 한화오션 (조선으로 분류)

        // ===== 금융지주 추가 =====
        stockSectors.put("071050", "금융"); // 한국금융지주
        stockSectors.put("000540", "금융"); // 흥국화재
        stockSectors.put("003530", "금융"); // 한화투자증권
        stockSectors.put("001290", "증권"); // 광주은행 -> NH투자증권
        stockSectors.put("192530", "금융"); // 시큐센

        // ===== 방위산업 =====
        stockSectors.put("012450", "방산"); // 한화에어로스페이스
        stockSectors.put("047810", "방산"); // 한국항공우주
        stockSectors.put("000880", "방산"); // 한화
        stockSectors.put("298040", "방산"); // 효성중공업
        stockSectors.put("064350", "방산"); // 현대로템
        stockSectors.put("001120", "방산"); // LIG넥스원

        // ===== 전력/에너지 =====
        stockSectors.put("015760", "전력"); // 한국전력
        stockSectors.put("034730", "전력"); // SK가스 -> SK에코플랜트
        stockSectors.put("036460", "전력"); // 한국가스공사
        stockSectors.put("034020", "전력"); // 두산에너빌리티
        stockSectors.put("267250", "전력"); // HD현대

        // ===== 항공/운송 =====
        stockSectors.put("003490", "항공"); // 대한항공
        stockSectors.put("020560", "항공"); // 아시아나항공
        stockSectors.put("003570", "항공"); // 티웨이항공 -> 삭제
        stockSectors.put("039130", "항공"); // 진에어 -> 삭제
        stockSectors.put("000120", "운송"); // CJ대한통운
        stockSectors.put("011150", "운송"); // CJ씨푸드 -> 음식료
        stockSectors.put("001510", "운송"); // SK증권 -> 증권
        stockSectors.put("000320", "운송"); // 세아홀딩스

        // ===== 호텔/레저 =====
        stockSectors.put("034230", "레저"); // 파라다이스
        stockSectors.put("008770", "레저"); // 호텔신라
        stockSectors.put("192400", "레저"); // 쿠쿠홀딩스
        stockSectors.put("035250", "레저"); // 강원랜드
        stockSectors.put("079160", "레저"); // CJ CGV

        // ===== 미디어/광고 =====
        stockSectors.put("030000", "미디어"); // 제일기획
        stockSectors.put("035760", "미디어"); // CJ ENM -> 미디어
        stockSectors.put("028260", "지주"); // 삼성물산

        // ===== 의료기기 =====
        stockSectors.put("084990", "의료기기"); // 헬릭스미스
        stockSectors.put("238090", "의료기기"); // 휴메딕스 -> 삭제

        // ===== 디스플레이 =====
        stockSectors.put("034220", "디스플레이"); // LG디스플레이
        stockSectors.put("011070", "디스플레이"); // LG이노텍

        return stockSectors.getOrDefault(stockCode, "기타");
    }

    /**
     * 업종별 SWOT 데이터
     */
    private Map<String, Map<String, List<String>>> getSectorSpecificSwot() {
        Map<String, Map<String, List<String>>> sectorSwot = new HashMap<>();

        // 반도체
        Map<String, List<String>> semiconductor = new HashMap<>();
        semiconductor.put("strength", Arrays.asList(
            "메모리 반도체 세계 시장 점유율 1위",
            "HBM(고대역폭메모리) 기술 리더십 확보",
            "대규모 설비 투자 능력 보유",
            "AI 서버용 반도체 수요 급증 대응 역량",
            "파운드리 사업 확장으로 포트폴리오 다각화"
        ));
        semiconductor.put("weakness", Arrays.asList(
            "메모리 가격 사이클에 따른 실적 변동성",
            "중국 반도체 굴기에 따른 경쟁 심화",
            "고정비 부담으로 수익성 압박 가능",
            "미중 기술 패권 갈등 속 지정학적 리스크"
        ));
        semiconductor.put("opportunity", Arrays.asList(
            "AI·데이터센터 투자 확대로 메모리 수요 급증",
            "자율주행·IoT 확산에 따른 차량용 반도체 성장",
            "온디바이스 AI 확산으로 모바일 메모리 고사양화",
            "미국 CHIPS법 수혜로 현지 생산 기반 강화"
        ));
        semiconductor.put("threat", Arrays.asList(
            "중국 내 판매 규제 강화 가능성",
            "공급 과잉 시 가격 급락 우려",
            "대만 TSMC와의 파운드리 경쟁 심화",
            "글로벌 경기 둔화에 따른 IT 투자 감소"
        ));
        sectorSwot.put("반도체", semiconductor);

        // 2차전지
        Map<String, List<String>> battery = new HashMap<>();
        battery.put("strength", Arrays.asList(
            "글로벌 전기차 배터리 시장 점유율 상위권",
            "완성차 업체와의 장기 공급 계약 확보",
            "차세대 전고체 배터리 R&D 선도",
            "북미·유럽 현지 생산 거점 구축 완료"
        ));
        battery.put("weakness", Arrays.asList(
            "리튬·니켈 등 원자재 가격 변동에 민감",
            "중국 CATL과의 가격 경쟁 심화",
            "대규모 투자로 인한 차입금 증가",
            "GM 볼트 리콜 등 품질 리스크 잔존"
        ));
        battery.put("opportunity", Arrays.asList(
            "미국 IRA법 세액공제 혜택 수혜",
            "전기차 침투율 상승에 따른 수요 증가",
            "ESS(에너지저장장치) 시장 급성장",
            "완성차 업체 전동화 전략 가속화"
        ));
        battery.put("threat", Arrays.asList(
            "LFP 배터리 채택 확대로 NCM 시장 잠식",
            "전기차 보조금 축소에 따른 수요 둔화",
            "유럽 배터리 규제 강화로 비용 증가",
            "나트륨이온 등 대체 기술 등장"
        ));
        sectorSwot.put("2차전지", battery);

        // 바이오/제약
        Map<String, List<String>> bio = new HashMap<>();
        bio.put("strength", Arrays.asList(
            "바이오시밀러 글로벌 시장 점유율 1위",
            "FDA·EMA 승인 파이프라인 다수 보유",
            "CMO(위탁생산) 수주 잔고 역대 최대",
            "mRNA·항체 플랫폼 기술 확보"
        ));
        bio.put("weakness", Arrays.asList(
            "신약 개발 실패 시 대규모 손실 가능",
            "임상시험 장기화로 현금흐름 부담",
            "바이오시밀러 가격 경쟁 심화",
            "핵심 인력 이탈 리스크"
        ));
        bio.put("opportunity", Arrays.asList(
            "글로벌 바이오의약품 시장 연 10% 성장",
            "ADC·CGT 등 차세대 치료제 수요 급증",
            "팬데믹 이후 백신·치료제 투자 확대",
            "빅파마 라이선스 아웃 딜 증가"
        ));
        bio.put("threat", Arrays.asList(
            "미국 약가 인하 정책 추진",
            "특허 만료에 따른 매출 감소",
            "중국 바이오기업과의 경쟁 심화",
            "FDA 규제 강화 가능성"
        ));
        sectorSwot.put("바이오", bio);

        // 자동차
        Map<String, List<String>> auto = new HashMap<>();
        auto.put("strength", Arrays.asList(
            "글로벌 전기차 판매 Top 5 진입",
            "E-GMP 전용 플랫폼 기반 라인업 확대",
            "제네시스 브랜드 프리미엄화 성공",
            "미국·유럽 현지 생산 체계 안정화"
        ));
        auto.put("weakness", Arrays.asList(
            "노사 갈등에 따른 생산 차질 리스크",
            "내연기관차 의존도 여전히 높음",
            "자율주행 SW 역량 테슬라 대비 부족",
            "원자재·물류비 상승으로 마진 압박"
        ));
        auto.put("opportunity", Arrays.asList(
            "IRA법 수혜로 북미 전기차 경쟁력 강화",
            "하이브리드 수요 급증으로 수익성 개선",
            "인도·동남아 시장 침투율 확대",
            "SDV(소프트웨어 정의 차량) 전환 가속"
        ));
        auto.put("threat", Arrays.asList(
            "테슬라·BYD 가격 인하 공세",
            "유럽 탄소 규제 강화로 벌금 리스크",
            "전기차 충전 인프라 확대 지연",
            "자율주행 사고 시 법적 리스크"
        ));
        sectorSwot.put("자동차", auto);

        // IT 플랫폼
        Map<String, List<String>> platform = new HashMap<>();
        platform.put("strength", Arrays.asList(
            "국내 검색·메신저 시장 압도적 점유율",
            "커머스·핀테크·콘텐츠 슈퍼앱 생태계",
            "클라우드·AI 서비스 사업 고성장",
            "웹툰·웹소설 글로벌 플랫폼 확보"
        ));
        platform.put("weakness", Arrays.asList(
            "광고 매출 경기 민감도 높음",
            "신사업 투자로 영업이익률 하락",
            "글로벌 빅테크 대비 AI 역량 격차",
            "개인정보 보호 규제 대응 비용 증가"
        ));
        platform.put("opportunity", Arrays.asList(
            "생성형 AI 검색 서비스 상용화",
            "일본·동남아 웹툰 플랫폼 성장",
            "B2B 클라우드·SaaS 시장 확대",
            "금융·헬스케어 데이터 사업 기회"
        ));
        platform.put("threat", Arrays.asList(
            "구글·OpenAI AI 검색 서비스 경쟁",
            "플랫폼 규제 강화(인앱결제 의무화 등)",
            "틱톡·인스타 등 숏폼 플랫폼과 경쟁",
            "경기 침체 시 광고 예산 축소"
        ));
        sectorSwot.put("IT플랫폼", platform);

        // 금융
        Map<String, List<String>> finance = new HashMap<>();
        finance.put("strength", Arrays.asList(
            "국내 금융 시장 점유율 선두권",
            "비은행 계열사 포트폴리오 다각화",
            "디지털 뱅킹 전환으로 비용 효율화",
            "안정적인 배당 성향 유지"
        ));
        finance.put("weakness", Arrays.asList(
            "부동산 PF 부실 우려에 따른 충당금",
            "저금리 시 NIM(순이자마진) 하락",
            "핀테크·인터넷은행과의 경쟁 심화",
            "해외 사업 비중 여전히 낮음"
        ));
        finance.put("opportunity", Arrays.asList(
            "금리 인상기 이자수익 증가",
            "WM(자산관리) 수익 비중 확대",
            "동남아 디지털 뱅킹 시장 진출",
            "ESG 금융 상품 수요 증가"
        ));
        finance.put("threat", Arrays.asList(
            "경기 침체 시 대출 부실 증가",
            "가계부채 규제 강화로 대출 성장 제한",
            "빅테크 금융업 진출 확대",
            "사이버 보안 리스크 증가"
        ));
        sectorSwot.put("금융", finance);

        // 철강
        Map<String, List<String>> steel = new HashMap<>();
        steel.put("strength", Arrays.asList(
            "세계 5위 철강 생산 능력 보유",
            "자동차·조선용 고부가 강재 기술력",
            "리튬·니켈 등 2차전지 소재 사업 확장",
            "수소환원제철 기술 선도"
        ));
        steel.put("weakness", Arrays.asList(
            "철강 가격 변동에 따른 실적 변동성",
            "탄소 배출량 규제 대응 비용 증가",
            "중국산 저가 철강 수입 증가",
            "에너지 비용 상승으로 마진 압박"
        ));
        steel.put("opportunity", Arrays.asList(
            "인프라 투자 확대로 철강 수요 증가",
            "그린스틸 프리미엄 가격 실현",
            "2차전지 양극재 사업 고성장",
            "인도·동남아 철강 수요 증가"
        ));
        steel.put("threat", Arrays.asList(
            "EU 탄소국경세(CBAM) 시행",
            "중국 철강 과잉 공급 지속",
            "건설 경기 둔화로 내수 감소",
            "대체 소재(알루미늄·탄소섬유) 확대"
        ));
        sectorSwot.put("철강", steel);

        // 화학
        Map<String, List<String>> chemical = new HashMap<>();
        chemical.put("strength", Arrays.asList(
            "석유화학·전지소재·첨단소재 포트폴리오",
            "배터리 양극재 생산 역량 세계 최대",
            "수직계열화로 원가 경쟁력 확보",
            "글로벌 생산기지 네트워크"
        ));
        chemical.put("weakness", Arrays.asList(
            "석유화학 업황 사이클 영향",
            "유가·납사 가격 변동에 민감",
            "대규모 투자로 재무 부담 증가",
            "중국 자급률 상승으로 경쟁 심화"
        ));
        chemical.put("opportunity", Arrays.asList(
            "전기차 배터리 소재 수요 급증",
            "친환경 플라스틱·바이오 소재 성장",
            "반도체·디스플레이 소재 고부가화",
            "수소경제 전환에 따른 사업 기회"
        ));
        chemical.put("threat", Arrays.asList(
            "중국 석유화학 공급 과잉",
            "환경 규제 강화로 비용 증가",
            "원유가 급등 시 마진 축소",
            "ESG 투자자 이탈 리스크"
        ));
        sectorSwot.put("화학", chemical);

        // 통신
        Map<String, List<String>> telecom = new HashMap<>();
        telecom.put("strength", Arrays.asList(
            "5G 네트워크 전국 커버리지 완료",
            "초고속인터넷·IPTV 시장 점유율 1위",
            "AI·클라우드 B2B 사업 확장",
            "안정적인 현금흐름과 배당 매력"
        ));
        telecom.put("weakness", Arrays.asList(
            "통신 요금 인하 압력 지속",
            "가입자 포화로 성장 정체",
            "콘텐츠 투자 비용 증가",
            "OTT 경쟁에 따른 미디어 수익성 악화"
        ));
        telecom.put("opportunity", Arrays.asList(
            "AI·클라우드 기업 시장 성장",
            "자율주행·스마트시티 인프라 수요",
            "6G 기술 선점 기회",
            "메타버스·XR 서비스 확대"
        ));
        telecom.put("threat", Arrays.asList(
            "정부 통신비 인하 정책",
            "빅테크 OTT 서비스 경쟁",
            "MVNO 저가 요금제 확산",
            "5G 투자비 회수 지연"
        ));
        sectorSwot.put("통신", telecom);

        // 게임
        Map<String, List<String>> game = new HashMap<>();
        game.put("strength", Arrays.asList(
            "리니지·배그 등 글로벌 IP 보유",
            "모바일·PC·콘솔 멀티플랫폼 개발력",
            "높은 영업이익률과 현금 창출력",
            "AI 기반 게임 개발 도구 선도"
        ));
        game.put("weakness", Arrays.asList(
            "신작 흥행 여부에 따른 실적 변동성",
            "확률형 아이템 규제 강화",
            "핵심 개발 인력 이탈 리스크",
            "기존 IP 의존도 높음"
        ));
        game.put("opportunity", Arrays.asList(
            "글로벌 게임 시장 연 10% 성장",
            "클라우드 게이밍 서비스 확산",
            "웹3·블록체인 게임 시장 확대",
            "중국 판호 발급 재개"
        ));
        game.put("threat", Arrays.asList(
            "중국 게임 규제 강화 지속",
            "넷플릭스 등 OTT와 여가 시간 경쟁",
            "PC방 이용 감소 추세",
            "게임 셧다운제 등 규제 강화"
        ));
        sectorSwot.put("게임", game);

        // 엔터테인먼트
        Map<String, List<String>> entertainment = new HashMap<>();
        entertainment.put("strength", Arrays.asList(
            "K-POP 글로벌 팬덤 기반 확보",
            "아티스트 IP 기반 다각화된 수익 구조",
            "위버스 등 자체 플랫폼 보유",
            "MD·굿즈 등 부가 수익 창출력",
            "글로벌 투어 티켓 파워"
        ));
        entertainment.put("weakness", Arrays.asList(
            "특정 아티스트 의존도 높음",
            "멤버 군입대에 따른 공백 리스크",
            "신인 그룹 육성 비용 부담",
            "팬덤 이슈에 따른 평판 리스크"
        ));
        entertainment.put("opportunity", Arrays.asList(
            "K-콘텐츠 글로벌 인기 지속",
            "메타버스·NFT 등 신사업 확장",
            "일본·동남아·중남미 시장 성장",
            "뮤지컬·드라마 등 IP 확장"
        ));
        entertainment.put("threat", Arrays.asList(
            "J-POP·C-POP 등 경쟁 심화",
            "아티스트 이적·분쟁 리스크",
            "공연장 인프라 부족",
            "저작권 분쟁 가능성"
        ));
        sectorSwot.put("엔터", entertainment);

        // 조선
        Map<String, List<String>> shipbuilding = new HashMap<>();
        shipbuilding.put("strength", Arrays.asList(
            "LNG선·초대형 컨테이너선 기술 세계 1위",
            "수주 잔고 3년치 이상 확보",
            "고부가가치 선박 중심 포트폴리오",
            "해양플랜트 EPC 역량 보유"
        ));
        shipbuilding.put("weakness", Arrays.asList(
            "강재·인건비 상승으로 마진 압박",
            "환율 변동에 따른 수익성 변동",
            "숙련 인력 부족으로 납기 지연 우려",
            "조선업 경기 사이클 민감도"
        ));
        shipbuilding.put("opportunity", Arrays.asList(
            "친환경 선박 교체 수요 급증",
            "LNG·암모니아 추진선 발주 증가",
            "카타르 LNG 프로젝트 수주 기대",
            "해상 풍력 설치선 시장 성장"
        ));
        shipbuilding.put("threat", Arrays.asList(
            "중국 조선업 경쟁력 강화",
            "글로벌 물동량 감소 시 발주 축소",
            "강재 가격 급등 리스크",
            "후판 공급 부족 가능성"
        ));
        sectorSwot.put("조선", shipbuilding);

        // 건설
        Map<String, List<String>> construction = new HashMap<>();
        construction.put("strength", Arrays.asList(
            "해외 대형 플랜트 시공 실적 풍부",
            "주택 브랜드 인지도 및 분양 경쟁력",
            "인프라·도시개발 사업 역량",
            "디벨로퍼형 사업 모델로 수익성 개선"
        ));
        construction.put("weakness", Arrays.asList(
            "부동산 경기 침체 시 분양률 하락",
            "원자재·인건비 상승으로 마진 축소",
            "해외 프로젝트 손실 리스크",
            "PF 대출 부실화 우려"
        ));
        construction.put("opportunity", Arrays.asList(
            "사우디·UAE 인프라 투자 확대",
            "국내 SOC 투자 증가",
            "신도시·재개발 사업 수주 기회",
            "원전·데이터센터 건설 수요 증가"
        ));
        construction.put("threat", Arrays.asList(
            "금리 인상으로 부동산 시장 위축",
            "인허가 지연으로 사업 차질",
            "건설 인력 수급 불균형",
            "해외 프로젝트 정치 리스크"
        ));
        sectorSwot.put("건설", construction);

        // 항공
        Map<String, List<String>> airline = new HashMap<>();
        airline.put("strength", Arrays.asList(
            "국적 대형항공사로 노선 네트워크 광범위",
            "화물 사업 수익성 높음",
            "마일리지 프로그램 고객 충성도",
            "항공기 보유 대수 국내 최다"
        ));
        airline.put("weakness", Arrays.asList(
            "유가 변동에 따른 연료비 부담",
            "LCC 경쟁 심화로 수익성 압박",
            "고정비 비중 높아 손익분기점 높음",
            "노사 갈등 리스크"
        ));
        airline.put("opportunity", Arrays.asList(
            "코로나 이후 국제선 여행 수요 회복",
            "중국 노선 정상화 기대",
            "프리미엄 이코노미 등 상품 다각화",
            "동남아·일본 관광 수요 지속 증가"
        ));
        airline.put("threat", Arrays.asList(
            "유가 급등 시 수익성 악화",
            "경기 침체로 여행 수요 감소",
            "탄소 배출 규제 강화",
            "전염병 재확산 리스크"
        ));
        sectorSwot.put("항공", airline);

        // 전력/에너지
        Map<String, List<String>> power = new HashMap<>();
        power.put("strength", Arrays.asList(
            "독점적 전력 공급 인프라 보유",
            "대규모 발전 설비 및 송배전망",
            "안정적인 전력 수요 기반",
            "신재생에너지 발전 역량 확대"
        ));
        power.put("weakness", Arrays.asList(
            "전기요금 규제로 원가 전가 제한",
            "연료비 상승 시 적자 확대",
            "노후 발전소 폐쇄 비용 부담",
            "원전 사고 시 평판 리스크"
        ));
        power.put("opportunity", Arrays.asList(
            "전기요금 인상으로 수익성 개선",
            "원전 수출 사업 확대",
            "재생에너지 RPS 의무 비율 증가",
            "전기차 충전 인프라 사업 기회"
        ));
        power.put("threat", Arrays.asList(
            "탈원전 정책 변동 리스크",
            "LNG 가격 급등 시 발전 비용 증가",
            "분산전원 확대로 수요 감소",
            "ESG 투자 기준 강화"
        ));
        sectorSwot.put("전력", power);

        // 정유
        Map<String, List<String>> refinery = new HashMap<>();
        refinery.put("strength", Arrays.asList(
            "대규모 정제 설비로 규모의 경제",
            "석유화학 수직계열화 완료",
            "중동·아시아 정유 제품 수출 네트워크",
            "윤활유·특수제품 고부가 라인업"
        ));
        refinery.put("weakness", Arrays.asList(
            "유가·정제마진 변동에 실적 민감",
            "친환경 전환 투자 비용 부담",
            "내연기관 수요 장기 감소 추세",
            "탄소 배출량 규제 대응 필요"
        ));
        refinery.put("opportunity", Arrays.asList(
            "항공유 수요 회복으로 마진 개선",
            "바이오연료·SAF 사업 확대",
            "수소 사업 진출 기회",
            "아시아 정유 수요 증가"
        ));
        refinery.put("threat", Arrays.asList(
            "전기차 확산으로 휘발유 수요 감소",
            "중동·중국 정유 설비 증설",
            "탄소국경세 도입 시 경쟁력 약화",
            "ESG 투자자 이탈 리스크"
        ));
        sectorSwot.put("정유", refinery);

        // 유통
        Map<String, List<String>> retail = new HashMap<>();
        retail.put("strength", Arrays.asList(
            "오프라인 유통 네트워크 전국 최다",
            "백화점·마트·편의점 통합 시너지",
            "자체 브랜드(PB) 상품 경쟁력",
            "VIP 고객 기반 로열티 프로그램"
        ));
        retail.put("weakness", Arrays.asList(
            "이커머스에 시장 점유율 잠식",
            "대형마트 규제로 영업 제한",
            "인건비·임대료 부담 증가",
            "온라인 물류 인프라 투자 지연"
        ));
        retail.put("opportunity", Arrays.asList(
            "명품·프리미엄 소비 증가",
            "옴니채널 전략으로 고객 경험 강화",
            "신선식품 당일배송 시장 성장",
            "해외 직구 대행 서비스 확대"
        ));
        retail.put("threat", Arrays.asList(
            "쿠팡·네이버 등 이커머스 경쟁",
            "소비 심리 위축 시 매출 감소",
            "대형마트 의무휴업일 규제",
            "알리·테무 등 해외 플랫폼 공습"
        ));
        sectorSwot.put("유통", retail);

        // 지주회사
        Map<String, List<String>> holding = new HashMap<>();
        holding.put("strength", Arrays.asList(
            "다각화된 계열사 포트폴리오",
            "안정적인 배당수익 확보",
            "지배구조 개선을 통한 가치 제고",
            "계열사 시너지 효과 창출"
        ));
        holding.put("weakness", Arrays.asList(
            "지주사 할인(NAV 대비 저평가) 지속",
            "계열사 실적에 종속적인 수익구조",
            "복잡한 지배구조로 의사결정 지연",
            "오너리스크에 대한 노출"
        ));
        holding.put("opportunity", Arrays.asList(
            "신성장 사업 진출 용이",
            "계열사 IPO를 통한 가치 실현",
            "지배구조 개편으로 할인 해소",
            "M&A를 통한 사업 확장"
        ));
        holding.put("threat", Arrays.asList(
            "공정거래법 규제 강화",
            "계열사 부실화 시 연쇄 영향",
            "지배구조 관련 소송 리스크",
            "스튜어드십 코드 활성화"
        ));
        sectorSwot.put("지주", holding);

        // 보험
        Map<String, List<String>> insurance = new HashMap<>();
        insurance.put("strength", Arrays.asList(
            "보험료 수입 기반 안정적 현금흐름",
            "대규모 운용자산 기반 투자수익",
            "장기 고객 기반 확보",
            "리스크 관리 노하우 축적"
        ));
        insurance.put("weakness", Arrays.asList(
            "저금리 환경에서 운용수익률 하락",
            "손해율 상승으로 수익성 악화",
            "신계약 성장 정체",
            "K-ICS 도입에 따른 자본 부담"
        ));
        insurance.put("opportunity", Arrays.asList(
            "금리 인상 시 투자수익 개선",
            "헬스케어·디지털보험 성장",
            "고령화에 따른 연금·건강보험 수요",
            "해외 시장 진출 확대"
        ));
        insurance.put("threat", Arrays.asList(
            "보험업법 규제 강화",
            "보험사기·소송 비용 증가",
            "인슈어테크 스타트업 경쟁",
            "자연재해 증가로 손해율 상승"
        ));
        sectorSwot.put("보험", insurance);

        // 증권
        Map<String, List<String>> securities = new HashMap<>();
        securities.put("strength", Arrays.asList(
            "위탁매매·IB·자산관리 균형 포트폴리오",
            "리테일 고객 기반 확보",
            "해외주식 거래 플랫폼 성장",
            "PI(자기매매) 역량 강화"
        ));
        securities.put("weakness", Arrays.asList(
            "시장 변동성에 따른 실적 변동",
            "브로커리지 수수료 경쟁 심화",
            "ELS 사태 등 판매 리스크",
            "자본 규모 대비 수익성 저조"
        ));
        securities.put("opportunity", Arrays.asList(
            "퇴직연금 DC형 전환 수혜",
            "IPO·M&A 시장 활성화",
            "개인투자자 해외주식 수요 증가",
            "로보어드바이저 서비스 확대"
        ));
        securities.put("threat", Arrays.asList(
            "증시 급락 시 수익 급감",
            "핀테크·비금융사 경쟁 진입",
            "불완전판매 규제 강화",
            "글로벌 금융시장 불안"
        ));
        sectorSwot.put("증권", securities);

        // 음식료
        Map<String, List<String>> food = new HashMap<>();
        food.put("strength", Arrays.asList(
            "필수소비재로 안정적 수요",
            "강력한 브랜드 파워와 유통망",
            "가격 전가력 보유",
            "해외 시장 진출 성과"
        ));
        food.put("weakness", Arrays.asList(
            "원자재(곡물·원당) 가격 변동",
            "내수 시장 성장 한계",
            "건강·웰빙 트렌드에 취약한 제품군",
            "물류비·인건비 상승 부담"
        ));
        food.put("opportunity", Arrays.asList(
            "K-푸드 해외 수출 확대",
            "간편식·밀키트 시장 성장",
            "건강기능식품 시장 확대",
            "동남아·미국 시장 진출"
        ));
        food.put("threat", Arrays.asList(
            "소비 심리 위축으로 판매 감소",
            "PB상품과의 가격 경쟁",
            "식품 안전 이슈 발생 시 타격",
            "환율 상승으로 원가 부담"
        ));
        sectorSwot.put("음식료", food);

        // 화장품
        Map<String, List<String>> cosmetics = new HashMap<>();
        cosmetics.put("strength", Arrays.asList(
            "K-뷰티 글로벌 인지도 상승",
            "중국·동남아 수출 채널 확보",
            "R&D 기반 신제품 개발력",
            "면세점·온라인 유통 다각화"
        ));
        cosmetics.put("weakness", Arrays.asList(
            "중국 시장 의존도 높음",
            "인디브랜드와의 경쟁 심화",
            "면세점 채널 매출 감소",
            "마케팅 비용 부담 증가"
        ));
        cosmetics.put("opportunity", Arrays.asList(
            "미국·유럽 시장 진출 확대",
            "클린뷰티·비건 제품 수요 증가",
            "중국 시장 회복 기대",
            "온라인 D2C 채널 성장"
        ));
        cosmetics.put("threat", Arrays.asList(
            "중국 애국소비 확산",
            "글로벌 브랜드와의 경쟁",
            "원료 가격 상승",
            "환경 규제 강화"
        ));
        sectorSwot.put("화장품", cosmetics);

        // 섬유/의류
        Map<String, List<String>> textile = new HashMap<>();
        textile.put("strength", Arrays.asList(
            "글로벌 SPA 브랜드 OEM 역량",
            "기능성 소재 기술력 보유",
            "베트남 등 해외 생산기지 구축",
            "안정적인 고객사 관계"
        ));
        textile.put("weakness", Arrays.asList(
            "저마진 구조의 OEM 사업",
            "원사·원단 가격 변동 민감",
            "인건비 상승 압박",
            "패션 트렌드 변화 대응 어려움"
        ));
        textile.put("opportunity", Arrays.asList(
            "친환경·리사이클 소재 수요 증가",
            "스포츠·아웃도어 시장 성장",
            "동남아 OEM 수요 확대",
            "자체 브랜드 육성"
        ));
        textile.put("threat", Arrays.asList(
            "중국·방글라데시 가격 경쟁",
            "글로벌 경기 침체로 의류 소비 감소",
            "빠른 트렌드 변화로 재고 리스크",
            "ESG 규제 강화"
        ));
        sectorSwot.put("섬유", textile);

        // 방위산업
        Map<String, List<String>> defense = new HashMap<>();
        defense.put("strength", Arrays.asList(
            "K-방산 수출 급증세",
            "정부 방위비 증액 수혜",
            "폴란드·UAE 등 대형 수주 성과",
            "우주항공 분야 기술 축적"
        ));
        defense.put("weakness", Arrays.asList(
            "정부 예산 의존도 높음",
            "대규모 선투자 필요",
            "수주-매출 인식 시차 발생",
            "해외 수출 인허가 리스크"
        ));
        defense.put("opportunity", Arrays.asList(
            "지정학적 긴장으로 글로벌 방위비 증가",
            "동유럽·중동 무기 수요 급증",
            "UAM·위성 등 신사업 확장",
            "미국 동맹국 무기 도입 증가"
        ));
        defense.put("threat", Arrays.asList(
            "정권 교체에 따른 정책 변화",
            "글로벌 방산업체 경쟁 심화",
            "원자재·부품 공급망 이슈",
            "기술 유출 리스크"
        ));
        sectorSwot.put("방산", defense);

        // 기계
        Map<String, List<String>> machinery = new HashMap<>();
        machinery.put("strength", Arrays.asList(
            "산업용 기계·자동화 설비 기술력",
            "수출 비중 높아 글로벌 경쟁력",
            "A/S·부품 사업으로 안정적 수익",
            "스마트팩토리 솔루션 역량"
        ));
        machinery.put("weakness", Arrays.asList(
            "경기 사이클에 민감한 수주",
            "중국 기계 저가 공세",
            "원자재·에너지 비용 상승",
            "숙련 기술인력 부족"
        ));
        machinery.put("opportunity", Arrays.asList(
            "제조업 자동화·로봇 투자 확대",
            "친환경 설비 교체 수요",
            "신흥국 인프라 투자 증가",
            "AI·IoT 기반 스마트 기계"
        ));
        machinery.put("threat", Arrays.asList(
            "글로벌 제조업 경기 둔화",
            "일본·독일 기계와 경쟁",
            "환율 변동으로 수익성 악화",
            "공급망 차질 리스크"
        ));
        sectorSwot.put("기계", machinery);

        // 해운
        Map<String, List<String>> shipping = new HashMap<>();
        shipping.put("strength", Arrays.asList(
            "글로벌 해운 물동량 회복세",
            "컨테이너·벌크 선대 규모",
            "장기 운송 계약 확보",
            "친환경 선박 교체 투자"
        ));
        shipping.put("weakness", Arrays.asList(
            "운임 변동에 따른 실적 급변",
            "유가 상승 시 연료비 부담",
            "공급 과잉 시 운임 하락",
            "대규모 선박 투자 자금 부담"
        ));
        shipping.put("opportunity", Arrays.asList(
            "글로벌 교역 정상화로 물동량 증가",
            "친환경 선박 규제로 노후 선박 퇴출",
            "북극 항로 개척",
            "물류 복합 서비스 확대"
        ));
        shipping.put("threat", Arrays.asList(
            "글로벌 경기 침체로 물동량 감소",
            "해운 동맹 재편에 따른 경쟁",
            "홍해 분쟁 등 지정학적 리스크",
            "탈탄소 규제 강화"
        ));
        sectorSwot.put("해운", shipping);

        // 시멘트
        Map<String, List<String>> cement = new HashMap<>();
        cement.put("strength", Arrays.asList(
            "내수 시장 과점 구조",
            "물류비 장벽으로 수입 제한적",
            "안정적인 현금흐름",
            "레미콘·골재 수직계열화"
        ));
        cement.put("weakness", Arrays.asList(
            "건설 경기에 연동된 수요",
            "탄소 배출량 높아 규제 리스크",
            "에너지 비용 비중 높음",
            "성장성 제한적"
        ));
        cement.put("opportunity", Arrays.asList(
            "SOC 투자 확대로 수요 증가",
            "친환경 시멘트 제품 개발",
            "폐기물 연료 활용 확대",
            "동남아 시장 진출"
        ));
        cement.put("threat", Arrays.asList(
            "부동산 침체로 건설 수요 감소",
            "탄소세 도입 시 비용 증가",
            "환경 규제 강화",
            "대체 건축자재 확산"
        ));
        sectorSwot.put("시멘트", cement);

        // 레저
        Map<String, List<String>> leisure = new HashMap<>();
        leisure.put("strength", Arrays.asList(
            "카지노·호텔 등 고마진 사업 구조",
            "관광 수요 회복세 수혜",
            "면세·리테일 복합 서비스",
            "프리미엄 고객층 확보"
        ));
        leisure.put("weakness", Arrays.asList(
            "경기 민감도 높은 업종",
            "중국 단체관광 의존도",
            "계절성에 따른 실적 변동",
            "인건비·임대료 부담"
        ));
        leisure.put("opportunity", Arrays.asList(
            "중국 관광 정상화 기대",
            "프리미엄 여행 수요 증가",
            "복합리조트 개발 기회",
            "MZ세대 체험 소비 확대"
        ));
        leisure.put("threat", Arrays.asList(
            "전염병 재확산 리스크",
            "해외 여행 선호로 내수 감소",
            "온라인 카지노 규제 완화",
            "지역 카지노 경쟁 심화"
        ));
        sectorSwot.put("레저", leisure);

        // 운송
        Map<String, List<String>> logistics = new HashMap<>();
        logistics.put("strength", Arrays.asList(
            "전국 물류 네트워크 구축",
            "이커머스 성장으로 택배 수요 증가",
            "포워딩·3PL 역량 확보",
            "냉장·냉동 물류 인프라"
        ));
        logistics.put("weakness", Arrays.asList(
            "인건비·유류비 비중 높음",
            "택배 단가 경쟁 심화",
            "라스트마일 배송 손익 악화",
            "물류센터 투자 부담"
        ));
        logistics.put("opportunity", Arrays.asList(
            "이커머스 물동량 지속 성장",
            "풀필먼트 서비스 확대",
            "해외 물류 네트워크 확장",
            "자동화·로봇 도입으로 효율화"
        ));
        logistics.put("threat", Arrays.asList(
            "쿠팡 등 자체 물류 경쟁",
            "택배 기사 처우 개선 비용",
            "유가 급등 시 수익성 악화",
            "경기 침체로 물동량 감소"
        ));
        sectorSwot.put("운송", logistics);

        // 전자 (가전)
        Map<String, List<String>> electronics = new HashMap<>();
        electronics.put("strength", Arrays.asList(
            "글로벌 가전 브랜드 파워",
            "프리미엄 가전 시장 선도",
            "스마트홈·IoT 기술력",
            "B2B 전장 부품 사업 성장"
        ));
        electronics.put("weakness", Arrays.asList(
            "가전 시장 성숙으로 성장 정체",
            "중국 가전업체 경쟁 심화",
            "원자재·물류비 부담",
            "환율 변동에 민감"
        ));
        electronics.put("opportunity", Arrays.asList(
            "에너지 효율 가전 수요 증가",
            "전기차·전장 부품 사업 확대",
            "인도·동남아 시장 성장",
            "구독형 가전 서비스 확산"
        ));
        electronics.put("threat", Arrays.asList(
            "글로벌 소비 둔화",
            "중국 하이센스·TCL 추격",
            "원자재 가격 급등",
            "가전 교체 주기 장기화"
        ));
        sectorSwot.put("전자", electronics);

        // 디스플레이
        Map<String, List<String>> display = new HashMap<>();
        display.put("strength", Arrays.asList(
            "OLED 패널 기술 세계 선도",
            "대형 TV·모니터 패널 점유율",
            "애플·삼성 등 프리미엄 고객사 확보",
            "차량용 디스플레이 성장"
        ));
        display.put("weakness", Arrays.asList(
            "LCD 패널 가격 하락 압력",
            "중국 BOE·CSOT와 경쟁 심화",
            "대규모 설비투자 부담",
            "TV 수요 감소"
        ));
        display.put("opportunity", Arrays.asList(
            "IT용 OLED 수요 급증",
            "폴더블·롤러블 신제품 확대",
            "차량용 디스플레이 시장 성장",
            "AR/VR 디스플레이 수요"
        ));
        display.put("threat", Arrays.asList(
            "중국 OLED 양산 확대",
            "LCD 공급 과잉 지속",
            "마이크로LED 등 신기술 등장",
            "전방 수요 둔화"
        ));
        sectorSwot.put("디스플레이", display);

        // 제약
        Map<String, List<String>> pharma = new HashMap<>();
        pharma.put("strength", Arrays.asList(
            "안정적인 의약품 수요",
            "제네릭 의약품 포트폴리오",
            "국내 의료보험 기반 안정 매출",
            "해외 수출 확대 중"
        ));
        pharma.put("weakness", Arrays.asList(
            "약가 인하 압력 지속",
            "R&D 성공률 불확실",
            "글로벌 제약사 대비 규모 열세",
            "원료의약품 수입 의존"
        ));
        pharma.put("opportunity", Arrays.asList(
            "고령화로 의약품 수요 증가",
            "바이오시밀러 시장 진출",
            "신약 라이선스 아웃 기회",
            "동남아·중남미 시장 확대"
        ));
        pharma.put("threat", Arrays.asList(
            "건강보험 재정 악화로 약가 규제",
            "글로벌 빅파마 경쟁",
            "제네릭 경쟁 심화",
            "원료 가격 상승"
        ));
        sectorSwot.put("제약", pharma);

        // IT서비스
        Map<String, List<String>> itservice = new HashMap<>();
        itservice.put("strength", Arrays.asList(
            "기업 디지털 전환 수요 수혜",
            "SI·SM 안정적 수익 기반",
            "클라우드 전환 사업 성장",
            "AI·데이터 분석 역량 강화"
        ));
        itservice.put("weakness", Arrays.asList(
            "인건비 비중 높아 마진 제한",
            "대기업 IT 자회사 경쟁",
            "공공 사업 저가 수주 관행",
            "개발 인력 수급 어려움"
        ));
        itservice.put("opportunity", Arrays.asList(
            "클라우드·SaaS 시장 고성장",
            "AI·빅데이터 솔루션 수요",
            "스마트시티·스마트팩토리 사업",
            "정보보안 시장 확대"
        ));
        itservice.put("threat", Arrays.asList(
            "IT 투자 예산 축소 가능성",
            "글로벌 SaaS 업체 경쟁",
            "오픈소스·노코드 확산",
            "개발자 임금 급등"
        ));
        sectorSwot.put("IT서비스", itservice);

        // 자동차부품
        Map<String, List<String>> autoparts = new HashMap<>();
        autoparts.put("strength", Arrays.asList(
            "완성차 업체 장기 공급 관계",
            "전동화 부품 기술 전환 역량",
            "글로벌 생산기지 네트워크",
            "모듈화로 부가가치 상승"
        ));
        autoparts.put("weakness", Arrays.asList(
            "완성차 업체 납품단가 인하 압력",
            "내연기관 부품 사업 축소",
            "원자재 가격 전가 어려움",
            "전기차 전환 투자 부담"
        ));
        autoparts.put("opportunity", Arrays.asList(
            "전기차 부품 수요 급증",
            "자율주행 센서·SW 사업",
            "하이브리드 차량 부품 성장",
            "인도·멕시코 생산 확대"
        ));
        autoparts.put("threat", Arrays.asList(
            "전기차 부품 수 감소로 시장 축소",
            "중국 부품사 가격 경쟁",
            "반도체 부족으로 생산 차질",
            "완성차 내재화 확대"
        ));
        sectorSwot.put("자동차부품", autoparts);

        // 미디어
        Map<String, List<String>> media = new HashMap<>();
        media.put("strength", Arrays.asList(
            "K-콘텐츠 글로벌 경쟁력",
            "광고·콘텐츠·커머스 통합 수익",
            "스트리밍 플랫폼 확보",
            "IP 기반 다각화 수익"
        ));
        media.put("weakness", Arrays.asList(
            "광고 경기 민감도 높음",
            "콘텐츠 제작비 급증",
            "글로벌 OTT와 경쟁",
            "방송 광고 감소 추세"
        ));
        media.put("opportunity", Arrays.asList(
            "글로벌 OTT 콘텐츠 수요",
            "K-드라마·K-예능 인기 지속",
            "디지털 광고 시장 성장",
            "IP 활용 게임·MD 사업"
        ));
        media.put("threat", Arrays.asList(
            "넷플릭스·디즈니+ 점유율 확대",
            "광고주 디지털 전환",
            "콘텐츠 흥행 실패 리스크",
            "유튜브·숏폼 플랫폼 경쟁"
        ));
        sectorSwot.put("미디어", media);

        // 기타 (기본값)
        Map<String, List<String>> others = new HashMap<>();
        others.put("strength", Arrays.asList(
            "업계 내 안정적인 시장 지위",
            "지속적인 연구개발 투자",
            "다각화된 사업 포트폴리오",
            "우수한 재무 건전성"
        ));
        others.put("weakness", Arrays.asList(
            "신규 사업 진출 시 리스크 존재",
            "글로벌 경쟁 심화에 따른 압박",
            "원자재 가격 변동 민감도",
            "환율 변동에 따른 실적 영향"
        ));
        others.put("opportunity", Arrays.asList(
            "신규 시장 진출 기회",
            "정부 정책 수혜 가능성",
            "디지털 전환을 통한 효율화",
            "친환경 사업 확장 기회"
        ));
        others.put("threat", Arrays.asList(
            "글로벌 경기 침체 우려",
            "규제 환경 변화 리스크",
            "신규 경쟁자 진입 위협",
            "기술 변화에 따른 사업 모델 위협"
        ));
        sectorSwot.put("기타", others);

        return sectorSwot;
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

        // 다양한 헤드라인 풀에서 선택
        List<String> allHeadlines = Arrays.asList(
            stockName + ", 4분기 영업이익 전년비 15% 증가",
            stockName + " CEO \"올해 신성장동력 확보에 총력\"",
            "외국인, " + stockName + " 3거래일 연속 순매수",
            "\"" + stockName + " 목표주가 상향\" 증권사 리포트 잇따라",
            stockName + ", 해외 수주 계약 2조원 규모 체결",
            stockName + " 자사주 500억원 매입 결정",
            stockName + ", ESG 경영 강화로 지속가능성 평가 'A'등급",
            "기관투자자 \"" + stockName + " 중장기 유망\"",
            stockName + ", 신규 사업 진출로 포트폴리오 다각화",
            stockName + " 배당금 20% 인상 예고",
            stockName + ", R&D 투자 확대로 기술 경쟁력 강화",
            stockName + " 2분기 매출 역대 최대 전망",
            stockName + ", 원가 절감으로 영업이익률 개선",
            stockName + " 신제품 출시 앞두고 기대감 상승",
            "애널리스트 \"" + stockName + " 저평가 구간\"",
            stockName + ", 글로벌 파트너십 체결로 해외 진출 가속",
            stockName + " 1분기 실적 컨센서스 상회 전망",
            stockName + ", 차세대 기술 개발 완료 발표",
            stockName + " 주가 52주 신고가 경신",
            stockName + ", 시장 점유율 확대 지속"
        );

        // 종목명 해시 기반으로 3개 선택 (같은 종목은 같은 헤드라인)
        Random random = new Random(stockName.hashCode());
        List<String> shuffled = new ArrayList<>(allHeadlines);
        Collections.shuffle(shuffled, random);

        return shuffled.subList(0, 3);
    }

    /**
     * 종목 코드로 종목명 조회 (Kospi200DataService 활용)
     */
    private String getStockNameByCode(String stockCode) {
        // 먼저 Kospi200DataService에서 조회
        Map<String, String> kospiNames = kospi200DataService.getStockCodeToName();
        if (kospiNames.containsKey(stockCode)) {
            return kospiNames.get(stockCode);
        }

        // 하드코딩된 Top 10 fallback
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
     * 종목 코드로 회사 설명 조회
     */
    private String getCompanyDescription(String stockCode) {
        Map<String, String> descriptions = new HashMap<>();

        // 반도체
        descriptions.put("005930", "메모리 반도체, 스마트폰, 가전 분야 글로벌 1위 종합 전자기업. DRAM·NAND 세계 시장 점유율 1위, 파운드리 사업 확장 중");
        descriptions.put("000660", "DRAM·NAND 플래시 메모리 전문 반도체 기업. HBM(고대역폭메모리) 분야 기술 선도, AI 서버용 반도체 수혜주");

        // 2차전지
        descriptions.put("373220", "전기차 배터리 글로벌 Top 3 기업. GM·테슬라 등 완성차 업체 공급, 북미·유럽 생산기지 구축");
        descriptions.put("006400", "전기차 배터리 및 에너지저장장치(ESS) 전문. 전고체 배터리 R&D 선도, BMW·리비안 공급");

        // 바이오/제약
        descriptions.put("207940", "바이오의약품 CMO(위탁생산) 세계 1위. 글로벌 제약사 대상 항체의약품 생산, 바이오시밀러 개발");
        descriptions.put("068270", "바이오시밀러 글로벌 1위 기업. 항체의약품 개발·생산, 유럽·미국 시장 점유율 확대 중");
        descriptions.put("326030", "중추신경계 신약 전문 바이오제약사. 뇌전증·파킨슨병 치료제 개발, 글로벌 라이선스 아웃 성과");

        // 자동차
        descriptions.put("005380", "국내 1위, 글로벌 Top 5 완성차 기업. 전기차·수소차 라인업 확대, 제네시스 프리미엄 브랜드 성장");
        descriptions.put("000270", "현대차그룹 계열 완성차 기업. EV6·EV9 등 전기차 라인업, 글로벌 시장 점유율 상승 중");
        descriptions.put("012330", "자동차 부품 및 모듈 전문 기업. 전동화 부품·자율주행 기술 투자, 현대차그룹 핵심 계열사");

        // IT플랫폼
        descriptions.put("035420", "국내 1위 검색포털·커머스 플랫폼. 네이버쇼핑·웹툰·클라우드 사업, 글로벌 AI 서비스 확장");
        descriptions.put("035720", "국민 메신저 카카오톡 운영사. 핀테크·모빌리티·엔터 등 슈퍼앱 생태계, 일본 라인과 경영통합");

        // 금융
        descriptions.put("105560", "국내 1위 금융지주회사. KB국민은행·KB증권·KB손해보험 등 종합금융 서비스 제공");
        descriptions.put("055550", "국내 2위 금융지주회사. 신한은행·신한카드·신한금융투자 등 계열사 보유, 동남아 진출 확대");
        descriptions.put("086790", "하나은행 중심 금융지주회사. 기업금융 강점, 글로벌 네트워크 확대 중");

        // 철강
        descriptions.put("005490", "국내 1위, 세계 5위 철강 기업. 자동차·조선용 고급 강재 생산, 2차전지 소재 사업 확장");

        // 통신
        descriptions.put("017670", "국내 1위 이동통신사. 5G 네트워크 선도, AI·클라우드 B2B 사업 확대");
        descriptions.put("030200", "유선통신·IPTV 1위 기업. 5G·기업통신 서비스, 데이터센터 사업 확장");

        // 게임
        descriptions.put("259960", "배틀그라운드(PUBG) 개발사. 글로벌 PC·모바일 게임 운영, 인도·동남아 시장 강세");
        descriptions.put("036570", "리니지 시리즈 개발사. MMORPG 전문, 글로벌 게임 퍼블리싱");
        descriptions.put("251270", "세븐나이츠·나혼렙 개발사. 모바일 게임 전문, 글로벌 시장 진출");
        descriptions.put("263750", "검은사막 개발사. 오픈월드 MMORPG 전문, 콘솔·PC·모바일 멀티플랫폼");

        // 엔터테인먼트
        descriptions.put("352820", "BTS·세븐틴 소속사. K-POP 글로벌 1위 엔터사, 위버스 플랫폼 운영");
        descriptions.put("041510", "에스파·NCT 소속사. K-POP 명가, 메타버스·AI 아티스트 기술 개발");

        // 조선
        descriptions.put("009540", "현대중공업그룹 지주사. 조선·해양플랜트·엔진 사업, LNG선 세계 1위");
        descriptions.put("010140", "삼성그룹 조선사. LNG선·드릴십 건조, 친환경 선박 기술 선도");

        // 건설
        descriptions.put("000720", "현대차그룹 계열 건설사. 국내 주택·해외 플랜트 사업, 사우디 네옴시티 참여");

        // 항공
        descriptions.put("003490", "국내 1위 항공사. 여객·화물 운송, 글로벌 노선 네트워크 보유");

        // 화학
        descriptions.put("051910", "석유화학·첨단소재 종합화학 기업. 배터리 양극재 세계 1위, 전기차 소재 성장");

        String sector = getSectorByStockCode(stockCode);
        String stockName = getStockNameByCode(stockCode);

        return descriptions.getOrDefault(stockCode,
            stockName + "은(는) " + sector + " 업종의 코스피 상장 기업입니다.");
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
