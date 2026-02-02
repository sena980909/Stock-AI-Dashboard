package com.stockai.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NaverStockService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> DEFAULT_SYMBOLS = List.of(
            "005930", "000660", "035420", "005380", "006400",
            "035720", "051910", "003670", "055550", "105560"
    );

    public List<Map<String, Object>> fetchRealTimeStocks() {
        log.info("[fetchRealTimeStocks] Fetching real-time data for {} stocks", DEFAULT_SYMBOLS.size());
        List<Map<String, Object>> result = new ArrayList<>();

        for (String symbol : DEFAULT_SYMBOLS) {
            try {
                Map<String, Object> stock = fetchStock(symbol);
                if (stock != null) {
                    result.add(stock);
                }
            } catch (Exception e) {
                log.warn("[fetchRealTimeStocks] Failed to fetch stock {}: {}", symbol, e.getMessage());
            }
        }

        // AI 점수 내림차순 정렬
        result.sort((a, b) -> (Integer) b.get("aiScore") - (Integer) a.get("aiScore"));
        log.info("[fetchRealTimeStocks] Successfully fetched {} stocks", result.size());
        return result;
    }

    /**
     * 네이버 API에서 종목 정보 조회 (시가총액 포함)
     */
    public Map<String, Object> fetchStock(String symbol) {
        try {
            String url = "https://m.stock.naver.com/api/stock/" + symbol + "/basic";
            log.debug("[fetchStock] Fetching stock data from: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());

            // 기본 정보 파싱
            String name = root.path("stockName").asText();
            String closePriceStr = root.path("closePrice").asText().replace(",", "");
            String changeStr = root.path("compareToPreviousClosePrice").asText().replace(",", "");
            String ratioStr = root.path("fluctuationsRatio").asText();
            String priceDirection = root.path("compareToPreviousPrice").path("code").asText();

            // 시가총액 파싱 (핵심 수정 부분)
            Long marketCap = parseMarketCap(root);

            int price = parseIntSafe(closePriceStr, 0);
            int change = parseIntSafe(changeStr, 0);
            double changePercent = parseDoubleSafe(ratioStr, 0.0);

            // code: 1=하락, 2=상승, 3=보합
            if ("1".equals(priceDirection)) {
                change = -Math.abs(change);
                changePercent = -Math.abs(changePercent);
            }

            // Mock AI 점수 (실제로는 AI 서비스에서 계산)
            int aiScore = generateAiScore(symbol, changePercent);
            String sentiment = changePercent > 0.5 ? "positive"
                    : changePercent < -0.5 ? "negative" : "neutral";

            Map<String, Object> stock = new LinkedHashMap<>();
            stock.put("symbol", symbol);
            stock.put("name", name);
            stock.put("price", price);
            stock.put("change", change);
            stock.put("changePercent", changePercent);
            stock.put("marketCap", marketCap);
            stock.put("aiScore", aiScore);
            stock.put("sentiment", sentiment);

            log.debug("[fetchStock] Parsed stock {}: price={}, marketCap={}", symbol, price, marketCap);
            return stock;
        } catch (Exception e) {
            log.error("[fetchStock] Error fetching stock {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * 시가총액 파싱 - 여러 필드에서 시도
     * 네이버 API는 marketValue, totalMarketValue, marketCap 등 다양한 필드명 사용
     */
    private Long parseMarketCap(JsonNode root) {
        // 시도할 필드명 목록
        String[] marketCapFields = {
                "marketValue", "totalMarketValue", "marketCap",
                "marketCapitalization", "stockItemTotalInfos"
        };

        for (String field : marketCapFields) {
            JsonNode node = root.path(field);
            if (!node.isMissingNode() && !node.isNull()) {
                Long value = parseMarketCapValue(node);
                if (value != null && value > 0) {
                    log.debug("[parseMarketCap] Found marketCap in field '{}': {}", field, value);
                    return value;
                }
            }
        }

        // stockItemTotalInfos 배열에서 시가총액 찾기
        JsonNode totalInfos = root.path("stockItemTotalInfos");
        if (totalInfos.isArray()) {
            for (JsonNode info : totalInfos) {
                String key = info.path("key").asText();
                if ("시가총액".equals(key) || "marketValue".equalsIgnoreCase(key)) {
                    String valueStr = info.path("value").asText();
                    Long value = parseMarketCapString(valueStr);
                    if (value != null && value > 0) {
                        log.debug("[parseMarketCap] Found marketCap in stockItemTotalInfos: {}", value);
                        return value;
                    }
                }
            }
        }

        // 추가 API 호출하여 시가총액 조회
        return fetchMarketCapFromDetailApi(root);
    }

    /**
     * JsonNode에서 시가총액 값 추출
     */
    private Long parseMarketCapValue(JsonNode node) {
        if (node.isNumber()) {
            return node.asLong();
        } else if (node.isTextual()) {
            return parseMarketCapString(node.asText());
        }
        return null;
    }

    /**
     * 문자열 시가총액 파싱 (콤마, 조, 억 단위 처리)
     */
    private Long parseMarketCapString(String valueStr) {
        if (valueStr == null || valueStr.isBlank()) {
            return null;
        }

        try {
            // 콤마 제거
            String cleaned = valueStr.replace(",", "").replace(" ", "").trim();

            // "조", "억" 단위 처리
            if (cleaned.contains("조")) {
                String[] parts = cleaned.split("조");
                long trillion = parseLongSafe(parts[0].trim(), 0) * 1_000_000_000_000L;
                long billion = 0;
                if (parts.length > 1 && parts[1].contains("억")) {
                    String billionStr = parts[1].replace("억", "").replace("원", "").trim();
                    billion = parseLongSafe(billionStr, 0) * 100_000_000L;
                }
                return trillion + billion;
            } else if (cleaned.contains("억")) {
                String billionStr = cleaned.replace("억", "").replace("원", "").trim();
                return parseLongSafe(billionStr, 0) * 100_000_000L;
            } else {
                // 숫자만 있는 경우 (원 단위로 가정)
                return parseLongSafe(cleaned.replace("원", ""), 0);
            }
        } catch (Exception e) {
            log.warn("[parseMarketCapString] Failed to parse: {}", valueStr);
            return null;
        }
    }

    /**
     * 상세 API에서 시가총액 조회
     */
    private Long fetchMarketCapFromDetailApi(JsonNode basicRoot) {
        try {
            String symbol = basicRoot.path("stockCode").asText();
            if (symbol.isBlank()) {
                symbol = basicRoot.path("itemCode").asText();
            }
            if (symbol.isBlank()) {
                return null;
            }

            // 시세 상세 API 호출
            String detailUrl = "https://m.stock.naver.com/api/stock/" + symbol + "/integration";

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    detailUrl, HttpMethod.GET, entity, String.class);

            JsonNode detailRoot = objectMapper.readTree(response.getBody());

            // totalInfos에서 시가총액 찾기 (키: "시총" 또는 "시가총액", code: "marketValue")
            JsonNode totalInfos = detailRoot.path("totalInfos");
            if (totalInfos.isArray()) {
                for (JsonNode info : totalInfos) {
                    String key = info.path("key").asText();
                    String code = info.path("code").asText();
                    // "시총", "시가총액" 키 또는 "marketValue" 코드로 찾기
                    if ("시총".equals(key) || "시가총액".equals(key) || "marketValue".equalsIgnoreCase(code)) {
                        String valueStr = info.path("value").asText();
                        Long marketCap = parseMarketCapString(valueStr);
                        if (marketCap != null && marketCap > 0) {
                            log.debug("[fetchMarketCapFromDetailApi] Found marketCap from totalInfos: {} -> {}", valueStr, marketCap);
                            return marketCap;
                        }
                    }
                }
            }

            // dealTrendInfos에서 시가총액 찾기
            JsonNode dealTrendInfos = detailRoot.path("dealTrendInfos");
            if (dealTrendInfos.isArray()) {
                for (JsonNode info : dealTrendInfos) {
                    String key = info.path("key").asText();
                    String code = info.path("code").asText();
                    if ("시총".equals(key) || "시가총액".equals(key) || "marketValue".equalsIgnoreCase(code)) {
                        String valueStr = info.path("value").asText();
                        Long marketCap = parseMarketCapString(valueStr);
                        if (marketCap != null && marketCap > 0) {
                            log.debug("[fetchMarketCapFromDetailApi] Found marketCap from dealTrendInfos: {} -> {}", valueStr, marketCap);
                            return marketCap;
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.debug("[fetchMarketCapFromDetailApi] Could not fetch detail API: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 종목 검색
     */
    public List<Map<String, Object>> searchStocks(String keyword) {
        log.info("[searchStocks] Searching stocks with keyword: {}", keyword);
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            String url = "https://m.stock.naver.com/api/json/search/searchListJson.nhn?keyword=" + keyword;

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode result = root.path("result");

            if (result.isArray()) {
                for (JsonNode item : result) {
                    String code = item.path("code").asText();
                    String name = item.path("name").asText();
                    String market = item.path("market").asText();

                    // 국내 주식만 필터링
                    if ("KOSPI".equalsIgnoreCase(market) || "KOSDAQ".equalsIgnoreCase(market)) {
                        Map<String, Object> stock = new LinkedHashMap<>();
                        stock.put("code", code);
                        stock.put("name", name);
                        stock.put("market", market);
                        results.add(stock);
                    }
                }
            }

            log.info("[searchStocks] Found {} results for keyword: {}", results.size(), keyword);
        } catch (Exception e) {
            log.error("[searchStocks] Error searching stocks: {}", e.getMessage());
        }

        return results;
    }

    private int generateAiScore(String symbol, double changePercent) {
        // 간이 AI 점수: 등락률 기반 + 종목별 가중치
        int base = 50;
        base += (int) (changePercent * 10);
        base = Math.max(10, Math.min(95, base));
        return base;
    }

    private int parseIntSafe(String str, int defaultValue) {
        try {
            return Integer.parseInt(str.replace(",", "").trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private long parseLongSafe(String str, long defaultValue) {
        try {
            return Long.parseLong(str.replace(",", "").trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private double parseDoubleSafe(String str, double defaultValue) {
        try {
            return Double.parseDouble(str.replace(",", "").trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
