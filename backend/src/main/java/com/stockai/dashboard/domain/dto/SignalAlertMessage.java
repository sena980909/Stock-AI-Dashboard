package com.stockai.dashboard.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 매수/매도 신호 알림 메시지 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalAlertMessage {

    private String type;           // 메시지 타입: SIGNAL_ALERT
    private String symbol;         // 종목 코드
    private String name;           // 종목명
    private String signal;         // 신호: buy, sell
    private BigDecimal price;      // 현재가
    private Integer aiScore;       // AI 점수
    private String reason;         // 신호 발생 이유
    private LocalDateTime timestamp;

    public static SignalAlertMessage buySignal(String symbol, String name,
                                                BigDecimal price, Integer aiScore, String reason) {
        return SignalAlertMessage.builder()
                .type("SIGNAL_ALERT")
                .symbol(symbol)
                .name(name)
                .signal("buy")
                .price(price)
                .aiScore(aiScore)
                .reason(reason)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static SignalAlertMessage sellSignal(String symbol, String name,
                                                 BigDecimal price, Integer aiScore, String reason) {
        return SignalAlertMessage.builder()
                .type("SIGNAL_ALERT")
                .symbol(symbol)
                .name(name)
                .signal("sell")
                .price(price)
                .aiScore(aiScore)
                .reason(reason)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
