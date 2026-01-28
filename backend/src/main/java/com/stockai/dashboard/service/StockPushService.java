package com.stockai.dashboard.service;

import com.stockai.dashboard.domain.dto.SignalAlertMessage;
import com.stockai.dashboard.domain.dto.StockUpdateMessage;
import com.stockai.dashboard.websocket.StockWebSocketController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 주식 데이터 실시간 푸시 서비스
 * Kafka Consumer 또는 스케줄러에서 호출하여 클라이언트에게 데이터를 전송합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPushService {

    private final StockWebSocketController webSocketController;

    /**
     * 주식 가격 업데이트를 모든 구독자에게 브로드캐스트
     */
    public void pushStockUpdate(String symbol, String name, BigDecimal price,
                                 BigDecimal change, Double changePercent,
                                 Integer aiScore, String sentiment) {
        StockUpdateMessage message = StockUpdateMessage.of(
                symbol, name, price, change, changePercent, aiScore, sentiment
        );

        // 전체 업데이트 채널로 브로드캐스트
        webSocketController.broadcastStockUpdate(message);

        // 종목별 채널로도 전송
        webSocketController.sendStockUpdate(symbol, message);

        log.debug("Pushed stock update for {}: price={}, aiScore={}",
                symbol, price, aiScore);
    }

    /**
     * AI 분석 결과에 따른 매수 신호 발생
     */
    public void pushBuySignal(String symbol, String name, BigDecimal price,
                               Integer aiScore, String reason) {
        SignalAlertMessage alert = SignalAlertMessage.buySignal(
                symbol, name, price, aiScore, reason
        );

        webSocketController.broadcastSignalAlert(alert);
        log.info("BUY signal pushed for {}: aiScore={}, reason={}",
                symbol, aiScore, reason);
    }

    /**
     * AI 분석 결과에 따른 매도 신호 발생
     */
    public void pushSellSignal(String symbol, String name, BigDecimal price,
                                Integer aiScore, String reason) {
        SignalAlertMessage alert = SignalAlertMessage.sellSignal(
                symbol, name, price, aiScore, reason
        );

        webSocketController.broadcastSignalAlert(alert);
        log.info("SELL signal pushed for {}: aiScore={}, reason={}",
                symbol, aiScore, reason);
    }

    /**
     * 특정 사용자에게 개인화된 알림 전송
     */
    public void pushPersonalAlert(String userId, String symbol, String name,
                                   BigDecimal price, Integer aiScore, String signal, String reason) {
        SignalAlertMessage alert = "buy".equals(signal)
                ? SignalAlertMessage.buySignal(symbol, name, price, aiScore, reason)
                : SignalAlertMessage.sellSignal(symbol, name, price, aiScore, reason);

        webSocketController.sendPersonalAlert(userId, alert);
        log.info("Personal {} alert sent to user {} for {}",
                signal, userId, symbol);
    }
}
