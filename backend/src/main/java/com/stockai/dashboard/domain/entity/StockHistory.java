package com.stockai.dashboard.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 일봉 데이터 (OHLCV) 엔티티
 * 캔들 차트 렌더링에 사용
 */
@Entity
@Table(name = "stock_history",
        indexes = {
            @Index(name = "idx_stock_history_code_date", columnList = "stock_code, trade_date DESC", unique = true)
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "open_price", precision = 15, scale = 2)
    private BigDecimal openPrice;

    @Column(name = "high_price", precision = 15, scale = 2)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 15, scale = 2)
    private BigDecimal lowPrice;

    @Column(name = "close_price", precision = 15, scale = 2)
    private BigDecimal closePrice;

    @Column(name = "volume")
    private Long volume;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 등락률 계산 (전일 종가 대비)
     */
    public BigDecimal calculateChangePercent(BigDecimal previousClose) {
        if (previousClose == null || previousClose.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return closePrice.subtract(previousClose)
                .divide(previousClose, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
