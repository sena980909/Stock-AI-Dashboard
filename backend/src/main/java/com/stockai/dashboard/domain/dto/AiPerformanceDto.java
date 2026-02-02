package com.stockai.dashboard.domain.dto;

import com.stockai.dashboard.domain.entity.RecommendationHistory;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPerformanceDto {

    // 집계 통계
    private Integer totalCount;          // 전체 추천 건수
    private Integer successCount;        // 수익 달성 건수
    private Double hitRate;              // 적중률 (%)
    private Double averageReturn;        // 평균 수익률 (%)

    // 최고/최저 종목
    private StockPerformance bestStock;  // 최고 수익 종목
    private StockPerformance worstStock; // 최저 수익 종목

    // 기간 정보
    private Integer periodDays;          // 집계 기간 (일)
    private LocalDate startDate;         // 시작일
    private LocalDate endDate;           // 종료일

    // 추가 통계
    private Double totalReturn;          // 누적 수익률 (단순 합계)
    private Integer winStreak;           // 연속 성공 횟수
    private Double sharpeRatio;          // 샤프 비율 (리스크 대비 수익)

    // 최근 추천 히스토리
    private List<RecommendationRecord> recentHistory;

    // 일별 통계
    private List<DailyStats> dailyStats;

    // === 중첩 클래스 ===

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockPerformance {
        private String stockCode;
        private String stockName;
        private Double returnRate;
        private LocalDate recoDate;
        private Integer aiScore;
        private Long buyPrice;
        private Long currentPrice;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationRecord {
        private Long id;
        private String stockCode;
        private String stockName;
        private LocalDate recoDate;
        private Integer aiScore;
        private Long buyPrice;
        private Long currentPrice;
        private Double profitRate;
        private Boolean isSuccess;
        private String signalType;
        private String recoReason;
        private Long holdingDays;

        public static RecommendationRecord from(RecommendationHistory entity) {
            return RecommendationRecord.builder()
                    .id(entity.getId())
                    .stockCode(entity.getStockCode())
                    .stockName(entity.getStockName())
                    .recoDate(entity.getRecoDate())
                    .aiScore(entity.getAiScore())
                    .buyPrice(entity.getBuyPrice() != null ? entity.getBuyPrice().longValue() : null)
                    .currentPrice(entity.getCurrentPrice() != null ? entity.getCurrentPrice().longValue() : null)
                    .profitRate(entity.getProfitRate())
                    .isSuccess(entity.getIsSuccess())
                    .signalType(entity.getSignalType())
                    .recoReason(entity.getRecoReason())
                    .holdingDays(entity.getHoldingDays())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyStats {
        private LocalDate date;
        private Integer totalCount;
        private Integer successCount;
        private Double hitRate;
        private Double avgReturn;
    }

    // === 유틸리티 메소드 ===

    /**
     * 적중률 계산 (소수점 1자리)
     */
    public static Double calculateHitRate(long successCount, long totalCount) {
        if (totalCount == 0) return 0.0;
        return BigDecimal.valueOf(successCount)
                .divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * 수익률 포맷팅
     */
    public String getFormattedHitRate() {
        return hitRate != null ? String.format("%.1f%%", hitRate) : "N/A";
    }

    public String getFormattedAverageReturn() {
        if (averageReturn == null) return "N/A";
        String sign = averageReturn >= 0 ? "+" : "";
        return String.format("%s%.2f%%", sign, averageReturn);
    }
}
