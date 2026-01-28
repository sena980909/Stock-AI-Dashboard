package com.stockai.dashboard.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 적중률 분석 결과 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HitRateDto {

    private Long totalCount;        // 전체 추천 수
    private Long successCount;      // 성공 수
    private Long failCount;         // 실패 수
    private Double avgProfitRate;   // 평균 수익률
    private Double hitRate;         // 적중률 (%)

    public HitRateDto(Long totalCount, Long successCount, Long failCount, Double avgProfitRate) {
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

    public Long getPendingCount() {
        return totalCount - successCount - failCount;
    }
}
