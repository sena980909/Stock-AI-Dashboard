package com.stockai.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockai.dashboard.domain.dto.StockHistoryDto;
import com.stockai.dashboard.domain.entity.StockHistory;
import com.stockai.dashboard.repository.StockHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockHistoryService {

    private final StockHistoryRepository stockHistoryRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> TARGET_STOCKS = List.of(
            "005930", "000660", "035420", "005380", "006400",
            "035720", "051910", "003670", "055550", "105560"
    );

    /**
     * 최근 N일간 일봉 데이터 조회
     * DB에 데이터가 없으면 네이버에서 즉시 수집
     */
    @Transactional
    public List<StockHistoryDto> getHistory(String stockCode, int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        List<StockHistory> histories = stockHistoryRepository
                .findRecentHistory(stockCode, startDate);

        // DB에 데이터가 없으면 네이버에서 수집
        if (histories.isEmpty()) {
            log.info("No history data for {}, fetching from Naver...", stockCode);
            fetchAndSaveHistory(stockCode);
            histories = stockHistoryRepository.findRecentHistory(stockCode, startDate);
        }

        List<StockHistoryDto> result = new ArrayList<>();
        for (int i = 0; i < histories.size(); i++) {
            StockHistory current = histories.get(i);
            StockHistory previous = i > 0 ? histories.get(i - 1) : null;
            result.add(StockHistoryDto.from(current, previous));
        }
        return result;
    }

    /**
     * 스케줄러: 매 10분마다 일봉 데이터 수집 (테스트용)
     * 운영환경에서는 장 마감 후 1회 실행으로 변경
     */
    @Scheduled(fixedRate = 600000) // 10분
    @Transactional
    public void collectDailyHistory() {
        log.info("Starting daily history collection...");

        for (String stockCode : TARGET_STOCKS) {
            try {
                fetchAndSaveHistory(stockCode);
            } catch (Exception e) {
                log.error("Failed to collect history for {}: {}", stockCode, e.getMessage());
            }
        }

        log.info("Daily history collection completed.");
    }

    /**
     * 네이버 금융에서 일봉 데이터 수집 및 저장
     */
    @Transactional
    public void fetchAndSaveHistory(String stockCode) {
        try {
            String url = "https://api.stock.naver.com/chart/domestic/item/" + stockCode
                    + "/day?startDateTime=" + LocalDate.now().minusDays(60).format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "&endDateTime=" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());

            // 네이버 API는 직접 배열로 반환
            if (root.isArray()) {
                int savedCount = 0;
                for (JsonNode info : root) {
                    String dateStr = info.path("localDate").asText();
                    LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.BASIC_ISO_DATE);

                    // 이미 존재하면 스킵
                    if (stockHistoryRepository.existsByStockCodeAndTradeDate(stockCode, date)) {
                        continue;
                    }

                    StockHistory history = StockHistory.builder()
                            .stockCode(stockCode)
                            .tradeDate(date)
                            .openPrice(BigDecimal.valueOf(info.path("openPrice").asDouble()))
                            .highPrice(BigDecimal.valueOf(info.path("highPrice").asDouble()))
                            .lowPrice(BigDecimal.valueOf(info.path("lowPrice").asDouble()))
                            .closePrice(BigDecimal.valueOf(info.path("closePrice").asDouble()))
                            .volume(info.path("accumulatedTradingVolume").asLong())
                            .build();

                    stockHistoryRepository.save(history);
                    savedCount++;
                }
                log.info("Saved {} history records for {}", savedCount, stockCode);
            }
        } catch (Exception e) {
            log.error("Error fetching history for {}: {}", stockCode, e.getMessage(), e);
        }
    }

    /**
     * 특정 종목 수동 수집
     */
    @Transactional
    public void collectHistoryForStock(String stockCode) {
        fetchAndSaveHistory(stockCode);
    }
}
