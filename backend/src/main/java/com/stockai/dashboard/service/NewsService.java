package com.stockai.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 실시간 뉴스 서비스
 * 네이버 증권 뉴스 크롤링
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NewsService {

    private static final String REDIS_KEY_NEWS = "news:latest";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Kospi200DataService kospi200DataService;

    // 감성 분석용 키워드
    private static final List<String> POSITIVE_KEYWORDS = Arrays.asList(
            "상승", "급등", "호재", "실적개선", "성장", "흑자", "최고", "신고가", "돌파",
            "매수", "추천", "상향", "기대", "호조", "증가", "확대", "수혜", "강세"
    );

    private static final List<String> NEGATIVE_KEYWORDS = Arrays.asList(
            "하락", "급락", "악재", "실적악화", "감소", "적자", "최저", "신저가", "붕괴",
            "매도", "하향", "우려", "부진", "감소", "축소", "손실", "약세", "위기"
    );

    /**
     * 최신 뉴스 조회
     */
    public List<Map<String, Object>> getLatestNews() {
        log.info("[getLatestNews] Fetching latest news");

        // 1. Redis 캐시 조회
        try {
            Object cached = redisTemplate.opsForValue().get(REDIS_KEY_NEWS);
            if (cached != null) {
                log.debug("[getLatestNews] Cache HIT");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> cachedNews = (List<Map<String, Object>>) cached;
                return cachedNews;
            }
        } catch (Exception e) {
            log.warn("[getLatestNews] Redis read failed: {}", e.getMessage());
        }

        log.info("[getLatestNews] Cache MISS - fetching from Naver");

        // 2. 네이버 증권 뉴스 크롤링
        List<Map<String, Object>> allNews = new ArrayList<>();

        // 주요 뉴스 가져오기
        allNews.addAll(fetchNaverFinanceNews());

        // 종목별 뉴스 가져오기 (주요 종목)
        List<String> majorStocks = Arrays.asList("005930", "000660", "035420", "005380", "035720");
        for (String stockCode : majorStocks) {
            List<Map<String, Object>> stockNews = fetchStockNews(stockCode);
            for (Map<String, Object> news : stockNews) {
                if (allNews.size() < 20 && !isDuplicate(allNews, news)) {
                    allNews.add(news);
                }
            }
        }

        // 3. 시간순 정렬
        allNews.sort((a, b) -> {
            String timeA = (String) a.get("publishedAt");
            String timeB = (String) b.get("publishedAt");
            return timeB.compareTo(timeA);
        });

        // 4. 최대 15개
        if (allNews.size() > 15) {
            allNews = allNews.subList(0, 15);
        }

        // 5. Redis에 캐싱
        try {
            if (!allNews.isEmpty()) {
                redisTemplate.opsForValue().set(REDIS_KEY_NEWS, allNews, CACHE_TTL);
                log.info("[getLatestNews] Cached {} news items", allNews.size());
            }
        } catch (Exception e) {
            log.warn("[getLatestNews] Redis write failed: {}", e.getMessage());
        }

        return allNews;
    }

    /**
     * 네이버 증권 주요 뉴스 크롤링
     */
    private List<Map<String, Object>> fetchNaverFinanceNews() {
        List<Map<String, Object>> newsList = new ArrayList<>();

        try {
            String url = "https://m.stock.naver.com/api/news/list?category=ranknews&page=1&pageSize=15";

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Referer", "https://m.stock.naver.com/");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            // API가 배열을 직접 반환하는 경우
            if (root.isArray()) {
                for (JsonNode item : root) {
                    Map<String, Object> news = parseNaverNewsItem(item);
                    if (news != null) {
                        newsList.add(news);
                    }
                }
            } else {
                // items 필드가 있는 경우
                JsonNode items = root.path("items");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        Map<String, Object> news = parseNaverNewsItem(item);
                        if (news != null) {
                            newsList.add(news);
                        }
                    }
                }
            }

            log.info("[fetchNaverFinanceNews] Fetched {} news items", newsList.size());
        } catch (Exception e) {
            log.error("[fetchNaverFinanceNews] Error: {}", e.getMessage());
        }

        return newsList;
    }

    /**
     * 네이버 뉴스 아이템 파싱 (새 형식)
     */
    private Map<String, Object> parseNaverNewsItem(JsonNode item) {
        try {
            String title = item.path("tit").asText();
            if (title.isEmpty()) {
                title = item.path("title").asText();
            }
            if (title.isEmpty()) {
                return null;
            }

            String summary = item.path("subcontent").asText();
            if (summary.isEmpty()) {
                summary = item.path("body").asText();
            }
            if (summary.isEmpty()) {
                summary = title;
            }

            // 요약 길이 제한
            if (summary.length() > 150) {
                summary = summary.substring(0, 150) + "...";
            }

            String source = item.path("ohnm").asText();
            if (source.isEmpty()) {
                source = item.path("officeName").asText("뉴스");
            }

            // 시간 파싱 (20260202174623 형식)
            String dateStr = item.path("dt").asText();
            String publishedAt = parseNaverDateTime(dateStr);

            // ID 생성
            String oid = item.path("oid").asText();
            String aid = item.path("aid").asText();
            String id = oid + "_" + aid;
            if (id.equals("_")) {
                id = UUID.randomUUID().toString().substring(0, 8);
            }

            // 감성 분석
            Map<String, Object> sentiment = analyzeSentiment(title + " " + summary);

            // 관련 종목 추출
            List<String> relatedStocks = extractRelatedStocks(title + " " + summary);

            Map<String, Object> news = new LinkedHashMap<>();
            news.put("id", id);
            news.put("title", title);
            news.put("summary", summary);
            news.put("sentiment", sentiment.get("sentiment"));
            news.put("sentimentScore", sentiment.get("score"));
            news.put("source", source);
            news.put("publishedAt", publishedAt);
            news.put("relatedStocks", relatedStocks);

            return news;
        } catch (Exception e) {
            log.debug("[parseNaverNewsItem] Error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 네이버 날짜 형식 파싱 (20260202174623 -> ISO 형식)
     */
    private String parseNaverDateTime(String dateStr) {
        try {
            if (dateStr == null || dateStr.length() < 14) {
                return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            // 20260202174623 형식
            int year = Integer.parseInt(dateStr.substring(0, 4));
            int month = Integer.parseInt(dateStr.substring(4, 6));
            int day = Integer.parseInt(dateStr.substring(6, 8));
            int hour = Integer.parseInt(dateStr.substring(8, 10));
            int minute = Integer.parseInt(dateStr.substring(10, 12));
            int second = Integer.parseInt(dateStr.substring(12, 14));

            LocalDateTime dt = LocalDateTime.of(year, month, day, hour, minute, second);
            return dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    /**
     * 종목별 뉴스 크롤링
     */
    private List<Map<String, Object>> fetchStockNews(String stockCode) {
        List<Map<String, Object>> newsList = new ArrayList<>();

        try {
            String url = "https://m.stock.naver.com/api/news/list?category=stock&code=" + stockCode + "&page=1&pageSize=5";

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Referer", "https://m.stock.naver.com/");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            JsonNode items = root.path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    Map<String, Object> news = parseNewsItem(item);
                    if (news != null) {
                        // 관련 종목 추가
                        @SuppressWarnings("unchecked")
                        List<String> relatedStocks = (List<String>) news.get("relatedStocks");
                        if (!relatedStocks.contains(stockCode)) {
                            relatedStocks.add(stockCode);
                        }
                        newsList.add(news);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[fetchStockNews] Error for {}: {}", stockCode, e.getMessage());
        }

        return newsList;
    }

    /**
     * 뉴스 아이템 파싱
     */
    private Map<String, Object> parseNewsItem(JsonNode item) {
        try {
            String title = item.path("title").asText();
            String summary = item.path("body").asText();
            if (summary.isEmpty()) {
                summary = title;
            }
            String source = item.path("officeName").asText();
            if (source.isEmpty()) {
                source = item.path("officeShortName").asText("뉴스");
            }

            // 시간 파싱
            String dateStr = item.path("datetime").asText();
            String publishedAt = parseDateTime(dateStr);

            // ID 생성
            String id = item.path("oid").asText() + "_" + item.path("aid").asText();
            if (id.equals("_")) {
                id = UUID.randomUUID().toString().substring(0, 8);
            }

            // 감성 분석
            Map<String, Object> sentiment = analyzeSentiment(title + " " + summary);

            // 관련 종목 추출
            List<String> relatedStocks = extractRelatedStocks(title + " " + summary);

            Map<String, Object> news = new LinkedHashMap<>();
            news.put("id", id);
            news.put("title", title);
            news.put("summary", summary.length() > 200 ? summary.substring(0, 200) + "..." : summary);
            news.put("sentiment", sentiment.get("sentiment"));
            news.put("sentimentScore", sentiment.get("score"));
            news.put("source", source);
            news.put("publishedAt", publishedAt);
            news.put("relatedStocks", relatedStocks);

            return news;
        } catch (Exception e) {
            log.debug("[parseNewsItem] Error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 날짜 시간 파싱
     */
    private String parseDateTime(String dateStr) {
        try {
            if (dateStr == null || dateStr.isEmpty()) {
                return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            // 다양한 형식 지원
            if (dateStr.contains("T")) {
                return dateStr;
            }
            // "2024-01-01 12:00:00" 형식
            return dateStr.replace(" ", "T");
        } catch (Exception e) {
            return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    /**
     * 감성 분석 (간단한 키워드 기반)
     */
    private Map<String, Object> analyzeSentiment(String text) {
        int positiveCount = 0;
        int negativeCount = 0;

        String lowerText = text.toLowerCase();

        for (String keyword : POSITIVE_KEYWORDS) {
            if (lowerText.contains(keyword)) {
                positiveCount++;
            }
        }

        for (String keyword : NEGATIVE_KEYWORDS) {
            if (lowerText.contains(keyword)) {
                negativeCount++;
            }
        }

        Map<String, Object> result = new HashMap<>();

        if (positiveCount > negativeCount) {
            result.put("sentiment", "positive");
            result.put("score", Math.min(0.9, 0.5 + (positiveCount - negativeCount) * 0.1));
        } else if (negativeCount > positiveCount) {
            result.put("sentiment", "negative");
            result.put("score", Math.max(-0.9, -0.5 - (negativeCount - positiveCount) * 0.1));
        } else {
            result.put("sentiment", "neutral");
            result.put("score", 0.0);
        }

        return result;
    }

    /**
     * 관련 종목 추출
     */
    private List<String> extractRelatedStocks(String text) {
        List<String> relatedStocks = new ArrayList<>();
        Map<String, String> stockNames = kospi200DataService.getStockCodeToName();

        for (Map.Entry<String, String> entry : stockNames.entrySet()) {
            if (text.contains(entry.getValue())) {
                relatedStocks.add(entry.getKey());
                if (relatedStocks.size() >= 3) break;
            }
        }

        return relatedStocks;
    }

    /**
     * 중복 체크
     */
    private boolean isDuplicate(List<Map<String, Object>> newsList, Map<String, Object> news) {
        String title = (String) news.get("title");
        return newsList.stream()
                .anyMatch(n -> title.equals(n.get("title")));
    }

    /**
     * 캐시 새로고침
     */
    public void refreshCache() {
        try {
            redisTemplate.delete(REDIS_KEY_NEWS);
            log.info("[refreshCache] News cache cleared");
        } catch (Exception e) {
            log.warn("[refreshCache] Failed: {}", e.getMessage());
        }
    }

    /**
     * 5분마다 자동 갱신
     */
    @Scheduled(fixedRate = 300000)
    public void scheduledRefresh() {
        log.debug("[scheduledRefresh] Refreshing news cache");
        refreshCache();
    }
}
