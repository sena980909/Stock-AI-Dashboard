package com.stockai.dashboard.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * WebSocket을 통해 전송되는 주식 업데이트 메시지 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockUpdateMessage {

    private String type;           // 메시지 타입: STOCK_UPDATE, SUBSCRIPTION_CONFIRMED
    private String symbol;         // 종목 코드
    private String name;           // 종목명
    private BigDecimal price;      // 현재가
    private BigDecimal change;     // 등락액
    private Double changePercent;  // 등락률
    private Integer aiScore;       // AI 점수 (0-100)
    private String sentiment;      // 감성: positive, negative, neutral
    private Long volume;           // 거래량
    private LocalDateTime timestamp;

    public static StockUpdateMessage of(String symbol, String name, BigDecimal price,
                                         BigDecimal change, Double changePercent,
                                         Integer aiScore, String sentiment) {
        return StockUpdateMessage.builder()
                .type("STOCK_UPDATE")
                .symbol(symbol)
                .name(name)
                .price(price)
                .change(change)
                .changePercent(changePercent)
                .aiScore(aiScore)
                .sentiment(sentiment)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
