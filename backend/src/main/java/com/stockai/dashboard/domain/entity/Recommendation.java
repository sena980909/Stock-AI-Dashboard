package com.stockai.dashboard.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 추천 기록 엔티티
 * 적중률 분석에 사용
 */
@Entity
@Table(name = "recommendation",
        indexes = {
            @Index(name = "idx_recommendation_code_date", columnList = "stock_code, reco_at DESC"),
            @Index(name = "idx_recommendation_result", columnList = "result, reco_at DESC")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    @Column(name = "stock_name", length = 100)
    private String stockName;

    @Column(name = "signal_type", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private SignalType signalType;

    @Column(name = "reco_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal recoPrice;

    @Column(name = "ai_score")
    private Integer aiScore;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "current_price", precision = 15, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "profit_rate", precision = 5, scale = 2)
    private BigDecimal profitRate;

    @Column(name = "result", length = 10)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RecoResult result = RecoResult.PENDING;

    @Column(name = "reco_at", nullable = false)
    private LocalDateTime recoAt;

    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;

    public enum SignalType {
        BUY, SELL
    }

    public enum RecoResult {
        SUCCESS,  // +3% 이상 수익
        FAIL,     // -3% 이하 손실
        PENDING   // 평가 대기 중
    }

    /**
     * 현재 가격으로 수익률 계산 및 결과 업데이트
     * @param newCurrentPrice 현재 가격
     * @param successThreshold 성공 기준 (예: 3.0 = 3%)
     * @param failThreshold 실패 기준 (예: -3.0 = -3%)
     */
    public void evaluate(BigDecimal newCurrentPrice, double successThreshold, double failThreshold) {
        this.currentPrice = newCurrentPrice;

        if (recoPrice == null || recoPrice.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        // 수익률 계산
        BigDecimal diff = currentPrice.subtract(recoPrice);
        if (signalType == SignalType.SELL) {
            diff = diff.negate(); // 매도 추천은 가격이 떨어져야 성공
        }
        this.profitRate = diff.divide(recoPrice, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        // 결과 판정
        double rate = profitRate.doubleValue();
        if (rate >= successThreshold) {
            this.result = RecoResult.SUCCESS;
        } else if (rate <= failThreshold) {
            this.result = RecoResult.FAIL;
        }
        // PENDING 상태 유지 (아직 기준에 도달하지 않음)

        this.evaluatedAt = LocalDateTime.now();
    }
}
