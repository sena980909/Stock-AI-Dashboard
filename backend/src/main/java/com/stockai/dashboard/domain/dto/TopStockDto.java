package com.stockai.dashboard.domain.dto;

import com.stockai.dashboard.domain.entity.StockAnalysis;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopStockDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer rank;
    private String stockCode;
    private String stockName;
    private Long currentPrice;
    private Double changePercent;
    private Long marketCap;
    private Integer aiScore;
    private String summary; // AI 한줄평
    private String signalType; // STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL
    private LocalDateTime updatedAt;

    /**
     * StockAnalysis 엔티티를 TopStockDto로 변환
     */
    public static TopStockDto from(StockAnalysis analysis, int rank) {
        return TopStockDto.builder()
                .rank(rank)
                .stockCode(analysis.getStockCode())
                .stockName(analysis.getStockName())
                .currentPrice(analysis.getCurrentPrice())
                .changePercent(analysis.getChangePercent())
                .marketCap(analysis.getMarketCap())
                .aiScore(analysis.getTotalScore())
                .summary(analysis.getSummary())
                .signalType(analysis.getSignalType())
                .updatedAt(analysis.getUpdatedAt())
                .build();
    }

    /**
     * 시가총액 포맷 (조/억 단위)
     */
    public String getFormattedMarketCap() {
        if (marketCap == null) return "-";

        if (marketCap >= 1_000_000_000_000L) {
            return String.format("%.1f조", marketCap / 1_000_000_000_000.0);
        } else if (marketCap >= 100_000_000L) {
            return String.format("%.0f억", marketCap / 100_000_000.0);
        }
        return String.format("%,d원", marketCap);
    }
}
