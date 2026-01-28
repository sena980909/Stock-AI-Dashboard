package com.stockai.dashboard.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 관심 종목 엔티티
 * 사용자별 관심 종목 관리
 */
@Entity
@Table(name = "interest_stock",
        indexes = {
            @Index(name = "idx_interest_stock_user", columnList = "user_id, display_order")
        },
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_interest_user_stock", columnNames = {"user_id", "stock_code"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterestStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    @Column(name = "stock_name", length = 100)
    private String stockName;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "memo", length = 500)
    private String memo;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
