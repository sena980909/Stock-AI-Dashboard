package com.stockai.dashboard.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockai.dashboard.domain.entity.StockAnalysis;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiReportDto implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String stockCode;
    private String stockName;
    private String sector;              // 업종 (반도체, 게임, 바이오 등)
    private String companyDescription;  // 회사 소개 요약
    private Long currentPrice;
    private Double changePercent;
    private Long marketCap;

    // AI 분석 결과
    private Integer totalScore;
    private Integer technicalScore;
    private Integer sentimentScore;
    private String signalType;
    private String summary;

    // SWOT 분석
    private SwotAnalysis swot;

    // 상세 분석 데이터
    private TechnicalIndicators technicalIndicators;
    private SentimentData sentimentData;

    private LocalDateTime analyzedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SwotAnalysis implements Serializable {
        private static final long serialVersionUID = 1L;

        private List<String> strengths;   // 강점
        private List<String> weaknesses;  // 약점
        private List<String> opportunities; // 기회
        private List<String> threats;     // 위협
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TechnicalIndicators implements Serializable {
        private static final long serialVersionUID = 1L;

        private String trend;           // UPTREND, DOWNTREND, SIDEWAYS
        private Double rsiValue;        // RSI 지표
        private String rsiSignal;       // OVERBOUGHT, OVERSOLD, NEUTRAL
        private Double macdValue;       // MACD
        private String macdSignal;      // BUY, SELL, NEUTRAL
        private String movingAverage;   // ABOVE_MA, BELOW_MA
        private Double volumeChange;    // 거래량 변화율
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SentimentData implements Serializable {
        private static final long serialVersionUID = 1L;

        private Integer positiveNewsCount;
        private Integer negativeNewsCount;
        private Integer neutralNewsCount;
        private Double overallSentiment; // -1.0 ~ 1.0
        private List<String> recentHeadlines;
    }

    /**
     * StockAnalysis 엔티티를 AiReportDto로 변환
     */
    public static AiReportDto from(StockAnalysis analysis) {
        AiReportDtoBuilder builder = AiReportDto.builder()
                .stockCode(analysis.getStockCode())
                .stockName(analysis.getStockName())
                .currentPrice(analysis.getCurrentPrice())
                .changePercent(analysis.getChangePercent())
                .marketCap(analysis.getMarketCap())
                .totalScore(analysis.getTotalScore())
                .technicalScore(analysis.getTechnicalScore())
                .sentimentScore(analysis.getSentimentScore())
                .signalType(analysis.getSignalType())
                .summary(analysis.getSummary())
                .analyzedAt(analysis.getUpdatedAt());

        // JSON 파싱하여 SWOT 등 상세 데이터 설정
        if (analysis.getReportJson() != null && !analysis.getReportJson().isBlank()) {
            try {
                ReportJsonData reportData = objectMapper.readValue(
                        analysis.getReportJson(), ReportJsonData.class);
                builder.swot(reportData.getSwot());
                builder.technicalIndicators(reportData.getTechnicalIndicators());
                builder.sentimentData(reportData.getSentimentData());
            } catch (JsonProcessingException e) {
                log.warn("[AiReportDto] Failed to parse reportJson for stockCode={}: {}",
                        analysis.getStockCode(), e.getMessage());
            }
        }

        return builder.build();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ReportJsonData {
        private SwotAnalysis swot;
        private TechnicalIndicators technicalIndicators;
        private SentimentData sentimentData;
    }
}
