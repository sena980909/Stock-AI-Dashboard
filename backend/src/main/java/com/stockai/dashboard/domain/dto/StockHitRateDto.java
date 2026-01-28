package com.stockai.dashboard.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 종목별 적중률 분석 결과 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockHitRateDto {

    private String stockCode;
    private String stockName;
    private Long totalCount;
    private Long successCount;
    private Long failCount;
    private Double avgProfitRate;
    private Double hitRate;

    public StockHitRateDto(String stockCode, String stockName, Long totalCount,
                           Long successCount, Long failCount, Double avgProfitRate) {
        this.stockCode = stockCode;
        this.stockName = stockName;
        this.totalCount = totalCount != null ? totalCount : 0L;
        this.successCount = successCount != null ? successCount : 0L;
        this.failCount = failCount != null ? failCount : 0L;
        this.avgProfitRate = avgProfitRate;
        calculateHitRate();
    }

    private void calculateHitRate() {
        if (totalCount == null || totalCount == 0) {
            this.hitRate = 0.0;
        } else {
            this.hitRate = BigDecimal.valueOf(successCount)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP)
                    .doubleValue();
        }
    }
}
