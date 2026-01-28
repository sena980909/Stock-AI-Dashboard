package com.stockai.dashboard.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "news",
        indexes = {
            @Index(name = "idx_news_published", columnList = "published_at DESC"),
            @Index(name = "idx_news_sentiment", columnList = "sentiment")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(length = 20)
    private String sentiment; // POSITIVE, NEGATIVE, NEUTRAL

    @Column(name = "sentiment_score", precision = 3, scale = 2)
    private BigDecimal sentimentScore; // -1.00 ~ 1.00

    @Column(length = 100)
    private String source;

    @Column(length = 500)
    private String url;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToMany
    @JoinTable(
            name = "news_stocks",
            joinColumns = @JoinColumn(name = "news_id"),
            inverseJoinColumns = @JoinColumn(name = "stock_id")
    )
    @Builder.Default
    private Set<Stock> relatedStocks = new HashSet<>();
}
