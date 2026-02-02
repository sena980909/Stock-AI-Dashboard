package com.stockai.dashboard.service;

import com.stockai.dashboard.domain.dto.StockUpdateMessage;
import com.stockai.dashboard.domain.entity.Stock;
import com.stockai.dashboard.repository.AIAnalysisRepository;
import com.stockai.dashboard.repository.StockPriceRepository;
import com.stockai.dashboard.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;
    private final AIAnalysisRepository aiAnalysisRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StockRankService stockRankService;

    private static final String STOCK_CACHE_PREFIX = "stock:";

    public Map<String, Object> getRecommendedStocks(int limit, String sortBy) {
        // AI 점수 기준 상위 종목 조회
        List<Map<String, Object>> stocks = new ArrayList<>();

        // TODO: 실제 구현 시 Repository에서 조회
        // 임시 Mock 데이터
        stocks.add(createMockStock("005930", "삼성전자", 71000, 500, 0.71, 85, "positive"));
        stocks.add(createMockStock("000660", "SK하이닉스", 135000, 2000, 1.50, 78, "positive"));
        stocks.add(createMockStock("035420", "NAVER", 215000, -1500, -0.69, 65, "neutral"));

        Map<String, Object> response = new HashMap<>();
        response.put("stocks", stocks);
        response.put("totalCount", stocks.size());
        response.put("lastUpdated", LocalDateTime.now());

        return response;
    }

    public StockUpdateMessage getCurrentPrice(String symbol) {
        // Redis 캐시에서 먼저 조회
        String cacheKey = STOCK_CACHE_PREFIX + symbol;
        StockUpdateMessage cached = (StockUpdateMessage) redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            return cached;
        }

        // DB에서 조회 후 캐시에 저장
        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new RuntimeException("Stock not found: " + symbol));

        // TODO: 실제 가격 조회 로직 구현
        return StockUpdateMessage.builder()
                .type("STOCK_UPDATE")
                .symbol(symbol)
                .name(stock.getName())
                .price(BigDecimal.valueOf(71000))
                .change(BigDecimal.valueOf(500))
                .changePercent(0.71)
                .aiScore(85)
                .sentiment("positive")
                .timestamp(LocalDateTime.now())
                .build();
    }

    public Map<String, Object> getStockHistory(String symbol, String period, String interval) {
        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new RuntimeException("Stock not found: " + symbol));

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = calculateStartDate(endDate, period);

        List<Map<String, Object>> data = new ArrayList<>();
        // TODO: 실제 구현 시 StockPriceRepository에서 조회

        Map<String, Object> response = new HashMap<>();
        response.put("symbol", symbol);
        response.put("data", data);

        return response;
    }

    public Map<String, Object> getAIAnalysis(String symbol) {
        // StockRankService의 AI 리포트 사용 (업종, SWOT 등 포함)
        var report = stockRankService.getAiReport(symbol);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", report);

        return response;
    }

    public List<Stock> searchStocks(String query) {
        return stockRepository.findBySymbolContainingOrNameContaining(query, query);
    }

    private Map<String, Object> createMockStock(String symbol, String name, int price,
                                                  int change, double changePercent,
                                                  int aiScore, String sentiment) {
        Map<String, Object> stock = new HashMap<>();
        stock.put("symbol", symbol);
        stock.put("name", name);
        stock.put("price", price);
        stock.put("change", change);
        stock.put("changePercent", changePercent);
        stock.put("aiScore", aiScore);
        stock.put("sentiment", sentiment);
        return stock;
    }

    private LocalDate calculateStartDate(LocalDate endDate, String period) {
        return switch (period) {
            case "1D" -> endDate.minusDays(1);
            case "1W" -> endDate.minusWeeks(1);
            case "1M" -> endDate.minusMonths(1);
            case "3M" -> endDate.minusMonths(3);
            case "1Y" -> endDate.minusYears(1);
            default -> endDate.minusMonths(1);
        };
    }
}
