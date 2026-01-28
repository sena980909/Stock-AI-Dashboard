package com.stockai.dashboard.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_analysis",
        indexes = @Index(name = "idx_ai_analysis_stock_date", columnList = "stock_id, analyzed_at DESC"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "ai_score")
    private Integer aiScore; // 0-100

    @Column(length = 10)
    private String recommendation; // BUY, SELL, HOLD

    @Column(columnDefinition = "TEXT")
    private String reasons; // JSON format

    @Column(name = "positive_news_count")
    private Integer positiveNewsCount;

    @Column(name = "negative_news_count")
    private Integer negativeNewsCount;

    @Column(name = "neutral_news_count")
    private Integer neutralNewsCount;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;
}
