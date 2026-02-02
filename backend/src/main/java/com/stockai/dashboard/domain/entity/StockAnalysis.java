package com.stockai.dashboard.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_analysis",
        indexes = {
                @Index(name = "idx_stock_analysis_code", columnList = "stock_code"),
                @Index(name = "idx_stock_analysis_updated", columnList = "updated_at DESC")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    @Column(name = "stock_name", length = 100)
    private String stockName;

    @Column(name = "total_score")
    private Integer totalScore; // 0-100, AI 투자 점수

    @Column(length = 500)
    private String summary; // AI 한줄평

    @Column(name = "report_json", columnDefinition = "TEXT")
    private String reportJson; // SWOT 분석 등 상세 내용 (JSON 형태)

    @Column(name = "signal_type", length = 20)
    private String signalType; // STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL

    @Column(name = "technical_score")
    private Integer technicalScore; // 기술적 분석 점수

    @Column(name = "sentiment_score")
    private Integer sentimentScore; // 뉴스 감성 점수

    @Column(name = "current_price")
    private Long currentPrice;

    @Column(name = "change_percent")
    private Double changePercent;

    @Column(name = "market_cap")
    private Long marketCap;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 종합 점수 계산 (기술적 지표 60% + 감성 분석 40%)
     */
    public void calculateTotalScore() {
        int techWeight = technicalScore != null ? technicalScore : 50;
        int sentWeight = sentimentScore != null ? sentimentScore : 50;
        this.totalScore = (int) Math.round(techWeight * 0.6 + sentWeight * 0.4);
    }

    /**
     * 매매 신호 결정
     */
    public void determineSignalType() {
        if (totalScore == null) {
            this.signalType = "NEUTRAL";
            return;
        }

        if (totalScore >= 80) {
            this.signalType = "STRONG_BUY";
        } else if (totalScore >= 65) {
            this.signalType = "BUY";
        } else if (totalScore >= 45) {
            this.signalType = "NEUTRAL";
        } else if (totalScore >= 30) {
            this.signalType = "SELL";
        } else {
            this.signalType = "STRONG_SELL";
        }
    }
}
