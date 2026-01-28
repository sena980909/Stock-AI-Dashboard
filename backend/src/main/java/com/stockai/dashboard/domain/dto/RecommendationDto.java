package com.stockai.dashboard.domain.dto;

import com.stockai.dashboard.domain.entity.Recommendation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 추천 기록 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationDto {

    private Long id;
    private String stockCode;
    private String stockName;
    private String signalType;
    private BigDecimal recoPrice;
    private Integer aiScore;
    private String reason;
    private BigDecimal currentPrice;
    private BigDecimal profitRate;
    private String result;
    private LocalDateTime recoAt;
    private LocalDateTime evaluatedAt;

    public static RecommendationDto from(Recommendation entity) {
        return RecommendationDto.builder()
                .id(entity.getId())
                .stockCode(entity.getStockCode())
                .stockName(entity.getStockName())
                .signalType(entity.getSignalType().name())
                .recoPrice(entity.getRecoPrice())
                .aiScore(entity.getAiScore())
                .reason(entity.getReason())
                .currentPrice(entity.getCurrentPrice())
                .profitRate(entity.getProfitRate())
                .result(entity.getResult().name())
                .recoAt(entity.getRecoAt())
                .evaluatedAt(entity.getEvaluatedAt())
                .build();
    }
}
