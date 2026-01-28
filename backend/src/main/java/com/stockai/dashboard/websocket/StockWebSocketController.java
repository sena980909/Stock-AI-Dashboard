package com.stockai.dashboard.websocket;

import com.stockai.dashboard.domain.dto.StockUpdateMessage;
import com.stockai.dashboard.domain.dto.SignalAlertMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * WebSocket 메시지 핸들러
 * 클라이언트로부터의 메시지를 처리하고, 브로드캐스트 기능을 제공합니다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class StockWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 클라이언트가 /app/subscribe로 메시지를 보내면
     * /topic/stock-updates를 구독하는 모든 클라이언트에게 응답
     */
    @MessageMapping("/subscribe")
    @SendTo("/topic/stock-updates")
    public StockUpdateMessage handleSubscription(String symbol) {
        log.info("Client subscribed to stock updates for: {}", symbol);
        return StockUpdateMessage.builder()
                .type("SUBSCRIPTION_CONFIRMED")
                .symbol(symbol)
                .build();
    }

    /**
     * 특정 종목의 실시간 업데이트를 모든 구독자에게 브로드캐스트
     */
    public void broadcastStockUpdate(StockUpdateMessage message) {
        log.debug("Broadcasting stock update: {}", message.getSymbol());
        messagingTemplate.convertAndSend("/topic/stock-updates", message);
    }

    /**
     * 특정 종목 채널로 업데이트 전송
     */
    public void sendStockUpdate(String symbol, StockUpdateMessage message) {
        log.debug("Sending stock update to /topic/stock/{}: {}", symbol, message);
        messagingTemplate.convertAndSend("/topic/stock/" + symbol, message);
    }

    /**
     * 매수/매도 신호 알림을 브로드캐스트
     */
    public void broadcastSignalAlert(SignalAlertMessage alert) {
        log.info("Broadcasting signal alert: {} - {}", alert.getSymbol(), alert.getSignal());
        messagingTemplate.convertAndSend("/topic/signals", alert);
    }

    /**
     * 특정 사용자에게 개인 알림 전송
     */
    public void sendPersonalAlert(String userId, SignalAlertMessage alert) {
        log.info("Sending personal alert to user {}: {}", userId, alert.getSymbol());
        messagingTemplate.convertAndSendToUser(userId, "/queue/alerts", alert);
    }
}
