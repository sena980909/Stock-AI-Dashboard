package com.stockai.dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockai.dashboard.domain.dto.AiReportDto;
import com.stockai.dashboard.domain.dto.TopStockDto;
import com.stockai.dashboard.service.StockRankService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StockRankController.class)
@DisplayName("StockRankController 통합 테스트")
class StockRankControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StockRankService stockRankService;

    @Nested
    @DisplayName("GET /api/stocks/top-rank")
    class GetTopRankTest {

        @Test
        @DisplayName("Top 10 종목 조회 성공")
        void shouldReturnTop10Stocks() throws Exception {
            // Given
            List<TopStockDto> mockData = createMockTop10List();
            given(stockRankService.getTop10Stocks()).willReturn(mockData);

            // When & Then
            mockMvc.perform(get("/api/stocks/top-rank")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.count", is(2)))
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].stockCode", is("005930")))
                    .andExpect(jsonPath("$.data[0].stockName", is("삼성전자")))
                    .andExpect(jsonPath("$.data[0].rank", is(1)))
                    .andExpect(jsonPath("$.data[0].aiScore", is(75)))
                    .andExpect(jsonPath("$.data[1].stockCode", is("000660")));

            verify(stockRankService).getTop10Stocks();
        }

        @Test
        @DisplayName("데이터가 없을 때 빈 리스트 반환")
        void shouldReturnEmptyListWhenNoData() throws Exception {
            // Given
            given(stockRankService.getTop10Stocks()).willReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(get("/api/stocks/top-rank")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.count", is(0)))
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }

        @Test
        @DisplayName("서비스 오류 시 500 에러 반환")
        void shouldReturn500WhenServiceError() throws Exception {
            // Given
            given(stockRankService.getTop10Stocks()).willThrow(new RuntimeException("Service error"));

            // When & Then
            mockMvc.perform(get("/api/stocks/top-rank")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", containsString("Service error")));
        }
    }

    @Nested
    @DisplayName("GET /api/stocks/{code}/ai-report")
    class GetAiReportTest {

        @Test
        @DisplayName("AI 리포트 조회 성공")
        void shouldReturnAiReport() throws Exception {
            // Given
            String stockCode = "005930";
            AiReportDto mockReport = createMockAiReport(stockCode);
            given(stockRankService.getAiReport(stockCode)).willReturn(mockReport);

            // When & Then
            mockMvc.perform(get("/api/stocks/{code}/ai-report", stockCode)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.stockCode", is(stockCode)))
                    .andExpect(jsonPath("$.data.stockName", is("삼성전자")))
                    .andExpect(jsonPath("$.data.totalScore", is(75)))
                    .andExpect(jsonPath("$.data.signalType", is("BUY")))
                    .andExpect(jsonPath("$.data.swot.strengths", hasSize(2)));

            verify(stockRankService).getAiReport(stockCode);
        }

        @Test
        @DisplayName("존재하지 않는 종목 조회 시 404 반환")
        void shouldReturn404WhenStockNotFound() throws Exception {
            // Given
            String stockCode = "999999";
            AiReportDto emptyReport = AiReportDto.builder().build();
            given(stockRankService.getAiReport(stockCode)).willReturn(emptyReport);

            // When & Then
            mockMvc.perform(get("/api/stocks/{code}/ai-report", stockCode)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", containsString("Stock not found")));
        }

        @Test
        @DisplayName("유효하지 않은 종목 코드 시 400 반환")
        void shouldReturn400WhenInvalidStockCode() throws Exception {
            // Given - 빈 문자열 (컨트롤러에서 path variable 매핑은 되지만 validation에서 실패)
            String invalidCode = "   ";

            // When & Then
            mockMvc.perform(get("/api/stocks/{code}/ai-report", invalidCode)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)));
        }

        @Test
        @DisplayName("서비스 오류 시 500 에러 반환")
        void shouldReturn500WhenServiceError() throws Exception {
            // Given
            String stockCode = "005930";
            given(stockRankService.getAiReport(stockCode)).willThrow(new RuntimeException("Service error"));

            // When & Then
            mockMvc.perform(get("/api/stocks/{code}/ai-report", stockCode)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", containsString("Service error")));
        }
    }

    @Nested
    @DisplayName("POST /api/stocks/top-rank/refresh")
    class RefreshCacheTest {

        @Test
        @DisplayName("캐시 갱신 성공")
        void shouldRefreshCacheSuccessfully() throws Exception {
            // Given
            doNothing().when(stockRankService).refreshTop10Cache();

            // When & Then
            mockMvc.perform(post("/api/stocks/top-rank/refresh")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.message", containsString("refreshed successfully")));

            verify(stockRankService).refreshTop10Cache();
        }

        @Test
        @DisplayName("캐시 갱신 실패 시 500 에러")
        void shouldReturn500WhenRefreshFails() throws Exception {
            // Given
            doThrow(new RuntimeException("Cache refresh failed")).when(stockRankService).refreshTop10Cache();

            // When & Then
            mockMvc.perform(post("/api/stocks/top-rank/refresh")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success", is(false)));
        }
    }

    @Nested
    @DisplayName("DELETE /api/stocks/{code}/cache")
    class InvalidateCacheTest {

        @Test
        @DisplayName("캐시 무효화 성공")
        void shouldInvalidateCacheSuccessfully() throws Exception {
            // Given
            String stockCode = "005930";
            doNothing().when(stockRankService).invalidateCache(stockCode);

            // When & Then
            mockMvc.perform(delete("/api/stocks/{code}/cache", stockCode)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.message", containsString("invalidated")));

            verify(stockRankService).invalidateCache(stockCode);
        }
    }

    // Helper methods

    private List<TopStockDto> createMockTop10List() {
        TopStockDto stock1 = TopStockDto.builder()
                .rank(1)
                .stockCode("005930")
                .stockName("삼성전자")
                .currentPrice(70000L)
                .changePercent(1.5)
                .marketCap(400_000_000_000_000L)
                .aiScore(75)
                .summary("외국인 매수세 유입으로 상승 모멘텀 강화")
                .signalType("BUY")
                .updatedAt(LocalDateTime.now())
                .build();

        TopStockDto stock2 = TopStockDto.builder()
                .rank(2)
                .stockCode("000660")
                .stockName("SK하이닉스")
                .currentPrice(150000L)
                .changePercent(0.8)
                .marketCap(120_000_000_000_000L)
                .aiScore(68)
                .summary("실적 개선 기대감으로 긍정적 전망")
                .signalType("BUY")
                .updatedAt(LocalDateTime.now())
                .build();

        return Arrays.asList(stock1, stock2);
    }

    private AiReportDto createMockAiReport(String stockCode) {
        AiReportDto.SwotAnalysis swot = AiReportDto.SwotAnalysis.builder()
                .strengths(Arrays.asList("글로벌 시장 점유율 1위", "안정적인 현금 흐름"))
                .weaknesses(Arrays.asList("원자재 가격 변동 리스크"))
                .opportunities(Arrays.asList("신규 시장 진출 기회"))
                .threats(Arrays.asList("글로벌 경기 침체 우려"))
                .build();

        AiReportDto.TechnicalIndicators technical = AiReportDto.TechnicalIndicators.builder()
                .trend("UPTREND")
                .rsiValue(55.0)
                .rsiSignal("NEUTRAL")
                .macdValue(15.5)
                .macdSignal("BUY")
                .movingAverage("ABOVE_MA")
                .volumeChange(12.5)
                .build();

        AiReportDto.SentimentData sentiment = AiReportDto.SentimentData.builder()
                .positiveNewsCount(8)
                .negativeNewsCount(2)
                .neutralNewsCount(5)
                .overallSentiment(0.6)
                .recentHeadlines(Arrays.asList(
                        "삼성전자, 분기 실적 시장 예상치 상회",
                        "삼성전자 신규 사업 확장 계획 발표"
                ))
                .build();

        return AiReportDto.builder()
                .stockCode(stockCode)
                .stockName("삼성전자")
                .currentPrice(70000L)
                .changePercent(1.5)
                .marketCap(400_000_000_000_000L)
                .totalScore(75)
                .technicalScore(70)
                .sentimentScore(80)
                .signalType("BUY")
                .summary("외국인 매수세 유입으로 상승 모멘텀 강화")
                .swot(swot)
                .technicalIndicators(technical)
                .sentimentData(sentiment)
                .analyzedAt(LocalDateTime.now())
                .build();
    }
}
