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
        List<Map<String, Object>> result = new ArrayList<>();

        for (String symbol : DEFAULT_SYMBOLS) {
            try {
                Map<String, Object> stock = fetchStock(symbol);
                if (stock != null) {
                    result.add(stock);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch stock {}: {}", symbol, e.getMessage());
            }
        }

        // AI 점수 내림차순 정렬
        result.sort((a, b) -> (Integer) b.get("aiScore") - (Integer) a.get("aiScore"));
        return result;
    }

    public Map<String, Object> fetchStock(String symbol) {
        try {
            String url = "https://m.stock.naver.com/api/stock/" + symbol + "/basic";

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());

            String name = root.path("stockName").asText();
            String closePriceStr = root.path("closePrice").asText().replace(",", "");
            String changeStr = root.path("compareToPreviousClosePrice").asText().replace(",", "");
            String ratioStr = root.path("fluctuationsRatio").asText();
            String priceDirection = root.path("compareToPreviousPrice").path("code").asText();

            int price = Integer.parseInt(closePriceStr);
            int change = Integer.parseInt(changeStr);
            double changePercent = Double.parseDouble(ratioStr);

            // code: 1=하락, 2=상승, 3=보합
            if ("1".equals(priceDirection)) {
                change = -change;
                changePercent = -changePercent;
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
            stock.put("aiScore", aiScore);
            stock.put("sentiment", sentiment);

            return stock;
        } catch (Exception e) {
            log.error("Error fetching stock {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    private int generateAiScore(String symbol, double changePercent) {
        // 간이 AI 점수: 등락률 기반 + 종목별 가중치
        int base = 50;
        base += (int) (changePercent * 10);
        base = Math.max(10, Math.min(95, base));
        return base;
    }
}
