package com.stockai.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockai.dashboard.domain.dto.AiReportDto;
import com.stockai.dashboard.domain.dto.TopStockDto;
import com.stockai.dashboard.domain.entity.StockAnalysis;
import com.stockai.dashboard.repository.StockAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@DisplayName("StockRankService 단위 테스트")
class StockRankServiceTest {

    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOperations;
    private StockAnalysisRepository stockAnalysisRepository;
    private NaverStockService naverStockService;
    private ObjectMapper objectMapper;
    private StockRankService stockRankService;

    private static final String REDIS_KEY_TOP10 = "dashboard:top10";
    private static final String REDIS_KEY_AI_REPORT_PREFIX = "ai:report:";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = Mockito.mock(RedisTemplate.class);
        valueOperations = Mockito.mock(ValueOperations.class);
        stockAnalysisRepository = Mockito.mock(StockAnalysisRepository.class);
        naverStockService = Mockito.mock(NaverStockService.class);
        objectMapper = new ObjectMapper();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        stockRankService = new StockRankService(
                redisTemplate,
                stockAnalysisRepository,
                naverStockService,
                objectMapper
        );
    }

    @Nested
    @DisplayName("getTop10Stocks() 테스트")
    class GetTop10StocksTest {

        @Test
        @DisplayName("Redis 캐시 히트 시 캐시된 데이터 반환")
        void shouldReturnCachedDataWhenCacheHit() {
            // Given
            List<LinkedHashMap<String, Object>> cachedList = createMockCachedList();
            given(valueOperations.get(REDIS_KEY_TOP10)).willReturn(cachedList);

            // When
            List<TopStockDto> result = stockRankService.getTop10Stocks();

            // Then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getStockCode()).isEqualTo("005930");
            assertThat(result.get(0).getStockName()).isEqualTo("삼성전자");
            verify(stockAnalysisRepository, never()).findTop10ByMarketCapDesc();
        }

        @Test
        @DisplayName("Redis 캐시 미스 시 DB 조회 후 캐싱")
        void shouldFetchFromDbAndCacheWhenCacheMiss() {
            // Given
            given(valueOperations.get(REDIS_KEY_TOP10)).willReturn(null);

            List<StockAnalysis> dbData = createMockStockAnalysisList();
            given(stockAnalysisRepository.findTop10ByMarketCapDesc()).willReturn(dbData);

            // When
            List<TopStockDto> result = stockRankService.getTop10Stocks();

            // Then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(2);
            verify(stockAnalysisRepository).findTop10ByMarketCapDesc();
            verify(valueOperations).set(eq(REDIS_KEY_TOP10), anyList(), any(Duration.class));
        }

        @Test
        @DisplayName("Redis 조회 실패 시 DB에서 조회")
        void shouldFallbackToDbWhenRedisError() {
            // Given
            given(valueOperations.get(REDIS_KEY_TOP10)).willThrow(new RuntimeException("Redis connection failed"));

            List<StockAnalysis> dbData = createMockStockAnalysisList();
            given(stockAnalysisRepository.findTop10ByMarketCapDesc()).willReturn(dbData);

            // When
            List<TopStockDto> result = stockRankService.getTop10Stocks();

            // Then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(2);
            verify(stockAnalysisRepository).findTop10ByMarketCapDesc();
        }
    }

    @Nested
    @DisplayName("getAiReport() 테스트")
    class GetAiReportTest {

        @Test
        @DisplayName("Redis 캐시 히트 시 캐시된 리포트 반환")
        void shouldReturnCachedReportWhenCacheHit() {
            // Given
            String stockCode = "005930";
            LinkedHashMap<String, Object> cachedReport = createMockCachedReport(stockCode);
            given(valueOperations.get(REDIS_KEY_AI_REPORT_PREFIX + stockCode)).willReturn(cachedReport);

            // When
            AiReportDto result = stockRankService.getAiReport(stockCode);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStockCode()).isEqualTo(stockCode);
            verify(stockAnalysisRepository, never()).findFirstByStockCodeOrderByUpdatedAtDesc(anyString());
        }

        @Test
        @DisplayName("Redis 캐시 미스 시 DB 조회 후 캐싱")
        void shouldFetchFromDbAndCacheWhenCacheMiss() {
            // Given
            String stockCode = "005930";
            given(valueOperations.get(REDIS_KEY_AI_REPORT_PREFIX + stockCode)).willReturn(null);

            StockAnalysis analysis = createMockStockAnalysis(stockCode, "삼성전자");
            given(stockAnalysisRepository.findFirstByStockCodeOrderByUpdatedAtDesc(stockCode))
                    .willReturn(Optional.of(analysis));

            // When
            AiReportDto result = stockRankService.getAiReport(stockCode);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStockCode()).isEqualTo(stockCode);
            assertThat(result.getStockName()).isEqualTo("삼성전자");
            verify(valueOperations).set(eq(REDIS_KEY_AI_REPORT_PREFIX + stockCode), any(), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("invalidateCache() 테스트")
    class InvalidateCacheTest {

        @Test
        @DisplayName("특정 종목 캐시 무효화")
        void shouldInvalidateCacheForStock() {
            // Given
            String stockCode = "005930";
            given(redisTemplate.delete(anyString())).willReturn(true);

            // When
            stockRankService.invalidateCache(stockCode);

            // Then
            verify(redisTemplate).delete(REDIS_KEY_AI_REPORT_PREFIX + stockCode);
            verify(redisTemplate).delete(REDIS_KEY_TOP10);
        }

        @Test
        @DisplayName("캐시 삭제 실패 시에도 예외 발생하지 않음")
        void shouldNotThrowExceptionWhenDeleteFails() {
            // Given
            String stockCode = "005930";
            given(redisTemplate.delete(anyString())).willThrow(new RuntimeException("Redis error"));

            // When & Then - 예외 발생하지 않음
            stockRankService.invalidateCache(stockCode);
        }
    }

    // Helper methods for creating mock data

    private List<LinkedHashMap<String, Object>> createMockCachedList() {
        List<LinkedHashMap<String, Object>> list = new ArrayList<>();

        LinkedHashMap<String, Object> stock1 = new LinkedHashMap<>();
        stock1.put("rank", 1);
        stock1.put("stockCode", "005930");
        stock1.put("stockName", "삼성전자");
        stock1.put("currentPrice", 70000L);
        stock1.put("changePercent", 1.5);
        stock1.put("marketCap", 400000000000000L);
        stock1.put("aiScore", 75);
        stock1.put("summary", "외국인 매수세 유입으로 상승 모멘텀 강화");
        stock1.put("signalType", "BUY");
        list.add(stock1);

        LinkedHashMap<String, Object> stock2 = new LinkedHashMap<>();
        stock2.put("rank", 2);
        stock2.put("stockCode", "000660");
        stock2.put("stockName", "SK하이닉스");
        stock2.put("currentPrice", 150000L);
        stock2.put("changePercent", 0.8);
        stock2.put("marketCap", 120000000000000L);
        stock2.put("aiScore", 68);
        stock2.put("summary", "실적 개선 기대감으로 긍정적 전망");
        stock2.put("signalType", "BUY");
        list.add(stock2);

        return list;
    }

    private List<StockAnalysis> createMockStockAnalysisList() {
        List<StockAnalysis> list = new ArrayList<>();
        list.add(createMockStockAnalysis("005930", "삼성전자"));
        list.add(createMockStockAnalysis("000660", "SK하이닉스"));
        return list;
    }

    private StockAnalysis createMockStockAnalysis(String stockCode, String stockName) {
        return StockAnalysis.builder()
                .id(1L)
                .stockCode(stockCode)
                .stockName(stockName)
                .currentPrice(70000L)
                .changePercent(1.5)
                .marketCap(400000000000000L)
                .totalScore(75)
                .technicalScore(70)
                .sentimentScore(80)
                .summary("외국인 매수세 유입으로 상승 모멘텀 강화")
                .signalType("BUY")
                .reportJson("{\"swot\":{\"strengths\":[\"글로벌 시장 점유율 1위\"],\"weaknesses\":[\"원자재 가격 변동 리스크\"]}}")
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private LinkedHashMap<String, Object> createMockCachedReport(String stockCode) {
        LinkedHashMap<String, Object> report = new LinkedHashMap<>();
        report.put("stockCode", stockCode);
        report.put("stockName", "삼성전자");
        report.put("currentPrice", 70000L);
        report.put("changePercent", 1.5);
        report.put("marketCap", 400000000000000L);
        report.put("totalScore", 75);
        report.put("technicalScore", 70);
        report.put("sentimentScore", 80);
        report.put("signalType", "BUY");
        report.put("summary", "외국인 매수세 유입으로 상승 모멘텀 강화");

        LinkedHashMap<String, Object> swot = new LinkedHashMap<>();
        swot.put("strengths", Arrays.asList("글로벌 시장 점유율 1위"));
        swot.put("weaknesses", Arrays.asList("원자재 가격 변동 리스크"));
        swot.put("opportunities", Arrays.asList("신규 시장 진출 기회"));
        swot.put("threats", Arrays.asList("글로벌 경기 침체 우려"));
        report.put("swot", swot);

        return report;
    }
}
