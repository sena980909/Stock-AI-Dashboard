package com.stockai.dashboard.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "recommendation_history",
       indexes = {
           @Index(name = "idx_reco_date", columnList = "recoDate"),
           @Index(name = "idx_stock_code_date", columnList = "stockCode, recoDate")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_stock_reco_date", columnNames = {"stockCode", "recoDate"})
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String stockCode;

    @Column(nullable = false, length = 50)
    private String stockName;

    @Column(nullable = false)
    private LocalDate recoDate;

    @Column(nullable = false)
    private Integer aiScore;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal buyPrice;

    @Column(precision = 15, scale = 2)
    private BigDecimal currentPrice;

    @Column
    private Double profitRate;

    @Column
    private Boolean isSuccess;

    @Column(length = 20)
    private String signalType;

    @Column(length = 200)
    private String recoReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 수익률 계산 및 성공 여부 판정
     */
    public void calculatePerformance(BigDecimal latestPrice) {
        if (latestPrice == null || buyPrice == null || buyPrice.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        this.currentPrice = latestPrice;

        // 수익률 계산: (현재가 - 매수가) / 매수가 * 100
        BigDecimal diff = latestPrice.subtract(buyPrice);
        BigDecimal rate = diff.divide(buyPrice, 6, RoundingMode.HALF_UP)
                              .multiply(BigDecimal.valueOf(100));

        this.profitRate = rate.setScale(2, RoundingMode.HALF_UP).doubleValue();
        this.isSuccess = this.profitRate > 0;
    }

    /**
     * 보유 기간 (일수) 계산
     */
    public long getHoldingDays() {
        return java.time.temporal.ChronoUnit.DAYS.between(recoDate, LocalDate.now());
    }
}
