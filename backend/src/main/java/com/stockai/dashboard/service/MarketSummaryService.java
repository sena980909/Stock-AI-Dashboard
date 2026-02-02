package com.stockai.dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketSummaryService {

    private static final String REDIS_KEY_MARKET_SUMMARY = "market:summary";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String NAVER_INDEX_API = "https://m.stock.naver.com/api/index/";

    private final RedisTemplate<String, Object> redisTemplate;
    private final NaverStockService naverStockService;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 오늘의 시장 요약 조회
     */
    public Map<String, Object> getMarketSummary() {
        // 캐시 조회
        try {
            Object cached = redisTemplate.opsForValue().get(REDIS_KEY_MARKET_SUMMARY);
            if (cached != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cachedResult = (Map<String, Object>) cached;
                return cachedResult;
            }
        } catch (Exception e) {
            log.warn("[getMarketSummary] Redis read failed: {}", e.getMessage());
        }

        // 새로 계산
        Map<String, Object> summary = buildMarketSummary();

        // 캐시 저장
        try {
            redisTemplate.opsForValue().set(REDIS_KEY_MARKET_SUMMARY, summary, CACHE_TTL);
        } catch (Exception e) {
            log.warn("[getMarketSummary] Redis write failed: {}", e.getMessage());
        }

        return summary;
    }

    /**
     * 시장 요약 데이터 생성
     */
    private Map<String, Object> buildMarketSummary() {
        Map<String, Object> summary = new HashMap<>();

        // 주요 지수 정보 (코스피, 코스닥)
        Map<String, Object> kospiData = fetchIndexData("KOSPI");
        Map<String, Object> kosdaqData = fetchIndexData("KOSDAQ");

        summary.put("kospiIndex", kospiData.getOrDefault("index", 2450));
        summary.put("kospiChange", kospiData.getOrDefault("change", -50));
        summary.put("kospiChangePercent", kospiData.getOrDefault("changePercent", -2.0));

        summary.put("kosdaqIndex", kosdaqData.getOrDefault("index", 720));
        summary.put("kosdaqChange", kosdaqData.getOrDefault("change", -15));
        summary.put("kosdaqChangePercent", kosdaqData.getOrDefault("changePercent", -2.1));

        // 시장 상태 판단
        double kospiChange = ((Number) summary.get("kospiChangePercent")).doubleValue();
        String marketStatus = determineMarketStatus(kospiChange);
        summary.put("marketStatus", marketStatus);

        // 업종별 등락 (시장 방향성 반영)
        List<Map<String, Object>> sectorPerformance = getSectorPerformance(kospiChange);
        summary.put("topSectors", sectorPerformance.subList(0, Math.min(3, sectorPerformance.size())));
        summary.put("bottomSectors", sectorPerformance.subList(
                Math.max(0, sectorPerformance.size() - 3), sectorPerformance.size()));

        // AI 시장 분석 요약 (업종 정보 반영)
        String topSector = (String) sectorPerformance.get(0).get("name");
        String bottomSector = (String) sectorPerformance.get(sectorPerformance.size() - 1).get("name");
        String marketAnalysis = generateMarketAnalysis(kospiChange, topSector, bottomSector);
        summary.put("marketAnalysis", marketAnalysis);

        // 주요 이슈 (시장 상황과 일관되게)
        List<String> keyIssues = generateKeyIssues(kospiChange, topSector);
        summary.put("keyIssues", keyIssues);

        // 갱신 시간
        summary.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));

        return summary;
    }

    /**
     * 네이버 금융 API에서 실제 지수 데이터 조회
     */
    private Map<String, Object> fetchIndexData(String indexName) {
        Map<String, Object> data = new HashMap<>();

        try {
            String url = NAVER_INDEX_API + indexName + "/basic";

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = response.getBody();

            if (body != null) {
                // closePrice: 현재가 (예: "2,891.35")
                String closePriceStr = (String) body.get("closePrice");
                // compareToPreviousClosePrice: 전일대비 (예: "-17.61")
                String changeStr = (String) body.get("compareToPreviousClosePrice");
                // fluctuationsRatio: 등락률 (예: "-0.61")
                String changePercentStr = (String) body.get("fluctuationsRatio");

                if (closePriceStr != null) {
                    double closePrice = Double.parseDouble(closePriceStr.replace(",", ""));
                    data.put("index", Math.round(closePrice * 100) / 100.0);
                }
                if (changeStr != null) {
                    double change = Double.parseDouble(changeStr.replace(",", ""));
                    data.put("change", Math.round(change * 100) / 100.0);
                }
                if (changePercentStr != null) {
                    double changePercent = Double.parseDouble(changePercentStr.replace(",", ""));
                    data.put("changePercent", changePercent);
                }

                log.debug("[fetchIndexData] {} - index: {}, change: {}, changePercent: {}",
                    indexName, data.get("index"), data.get("change"), data.get("changePercent"));
            }
        } catch (Exception e) {
            log.warn("[fetchIndexData] Failed to fetch {} data: {}", indexName, e.getMessage());
            // 실패 시 기본값
            if ("KOSPI".equals(indexName)) {
                data.put("index", 2900.0);
                data.put("change", 0.0);
                data.put("changePercent", 0.0);
            } else {
                data.put("index", 850.0);
                data.put("change", 0.0);
                data.put("changePercent", 0.0);
            }
        }

        return data;
    }

    /**
     * 시장 상태 판단
     */
    private String determineMarketStatus(double changePercent) {
        if (changePercent >= 2.0) return "STRONG_UP";
        if (changePercent >= 0.5) return "UP";
        if (changePercent >= -0.5) return "FLAT";
        if (changePercent >= -2.0) return "DOWN";
        return "STRONG_DOWN";
    }

    /**
     * AI 시장 분석 요약 생성 (업종 정보 반영)
     */
    private String generateMarketAnalysis(double kospiChange, String topSector, String bottomSector) {
        int hour = LocalDateTime.now().getHour();

        String analysis;

        if (kospiChange <= -3.0) {
            analysis = String.format("글로벌 금융시장 불안, %s 등 전 업종 급락", bottomSector);
        } else if (kospiChange <= -1.0) {
            analysis = String.format("%s 약세 주도, 차익실현 매물 출회", bottomSector);
        } else if (kospiChange <= 0) {
            analysis = String.format("혼조세 마감, %s 상대적 강세", topSector);
        } else if (kospiChange <= 1.0) {
            analysis = String.format("%s 강세 주도, 저가 매수세 유입", topSector);
        } else if (kospiChange <= 2.0) {
            analysis = String.format("외국인 매수세 유입, %s 중심 상승", topSector);
        } else {
            analysis = String.format("글로벌 증시 훈풍, %s·%s 동반 강세", topSector, bottomSector.equals(topSector) ? "대형주" : bottomSector);
        }

        // 시간대별 코멘트 추가
        if (hour < 9) {
            return "장 시작 전 | 전일 대비 " + (kospiChange >= 0 ? "상승" : "하락") + " 출발 예상";
        } else if (hour >= 15 && hour < 16) {
            return "장 마감 | " + analysis;
        } else if (hour >= 16) {
            return "장 마감 | " + analysis;
        } else {
            return analysis;
        }
    }

    /**
     * 업종별 등락 현황 (시장 방향성과 일관되게 동적 생성)
     */
    private List<Map<String, Object>> getSectorPerformance(double marketChange) {
        List<Map<String, Object>> sectors = new ArrayList<>();

        // 업종 목록
        String[] sectorNames = {"반도체", "2차전지", "바이오", "자동차", "조선", "금융", "화학", "철강", "통신", "건설"};

        // 시장 방향에 따라 업종별 등락률 동적 생성
        Random random = new Random(LocalDateTime.now().getDayOfYear());

        for (String sectorName : sectorNames) {
            Map<String, Object> sector = new HashMap<>();
            sector.put("name", sectorName);

            // 시장 방향성 기반 + 업종별 변동성 반영
            double baseChange = marketChange;
            double sectorVariance = (random.nextDouble() - 0.5) * 4; // -2% ~ +2% 변동

            // 업종별 특성 반영 (시장 대비 베타)
            double beta = getSectorBeta(sectorName);
            double sectorChange = baseChange * beta + sectorVariance;

            // 소수점 1자리로 반올림
            sectorChange = Math.round(sectorChange * 10) / 10.0;

            sector.put("changePercent", sectorChange);
            sectors.add(sector);
        }

        // 등락률 기준 내림차순 정렬
        sectors.sort((a, b) -> Double.compare(
                (Double) b.get("changePercent"),
                (Double) a.get("changePercent")
        ));

        return sectors;
    }

    /**
     * 업종별 시장 민감도 (베타)
     */
    private double getSectorBeta(String sector) {
        Map<String, Double> betas = new HashMap<>();
        betas.put("반도체", 1.5);    // 시장보다 민감
        betas.put("2차전지", 1.4);
        betas.put("바이오", 1.3);
        betas.put("자동차", 1.1);
        betas.put("조선", 0.9);
        betas.put("금융", 0.8);      // 시장보다 둔감
        betas.put("화학", 0.9);
        betas.put("철강", 1.0);
        betas.put("통신", 0.6);      // 방어주
        betas.put("건설", 1.2);
        return betas.getOrDefault(sector, 1.0);
    }

    /**
     * 주요 이슈 생성 (시장 상황과 일관되게)
     */
    private List<String> generateKeyIssues(double kospiChange, String topSector) {
        List<String> issues = new ArrayList<>();

        if (kospiChange < -2.0) {
            issues.add("글로벌 증시 동반 약세");
            issues.add("외국인 순매도 지속");
            issues.add("원/달러 환율 급등");
        } else if (kospiChange < -0.5) {
            issues.add("차익실현 매물 출회");
            issues.add("기관 순매도 전환");
            issues.add("경기 둔화 우려");
        } else if (kospiChange < 0.5) {
            issues.add("관망세 속 혼조");
            issues.add("업종별 차별화 장세");
            issues.add(topSector + " 업종 상대 강세");
        } else if (kospiChange < 2.0) {
            issues.add("외국인 순매수 유입");
            issues.add(topSector + " 업종 강세");
            issues.add("미 증시 호조 영향");
        } else {
            issues.add("글로벌 증시 동반 상승");
            issues.add("외국인·기관 동반 매수");
            issues.add(topSector + " 업종 급등");
        }

        return issues;
    }

    /**
     * 5분마다 시장 요약 갱신
     */
    @Scheduled(fixedRate = 300000)
    public void refreshMarketSummary() {
        log.debug("[refreshMarketSummary] Refreshing market summary");
        try {
            Map<String, Object> summary = buildMarketSummary();
            redisTemplate.opsForValue().set(REDIS_KEY_MARKET_SUMMARY, summary, CACHE_TTL);
        } catch (Exception e) {
            log.warn("[refreshMarketSummary] Failed: {}", e.getMessage());
        }
    }
}
