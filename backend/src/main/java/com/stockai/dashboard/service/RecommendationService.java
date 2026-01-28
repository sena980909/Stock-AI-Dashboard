package com.stockai.dashboard.service;

import com.stockai.dashboard.domain.dto.HitRateDto;
import com.stockai.dashboard.domain.dto.RecommendationDto;
import com.stockai.dashboard.domain.dto.StockHitRateDto;
import com.stockai.dashboard.domain.entity.Recommendation;
import com.stockai.dashboard.domain.entity.Recommendation.RecoResult;
import com.stockai.dashboard.domain.entity.Recommendation.SignalType;
import com.stockai.dashboard.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final NaverStockService naverStockService;

    private static final double SUCCESS_THRESHOLD = 3.0;  // 성공 기준: +3%
    private static final double FAIL_THRESHOLD = -3.0;    // 실패 기준: -3%

    /**
     * AI 추천 기록 저장
     */
    @Transactional
    public RecommendationDto saveRecommendation(String stockCode, String stockName,
                                                 SignalType signalType, BigDecimal price,
                                                 Integer aiScore, String reason) {
        Recommendation recommendation = Recommendation.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .signalType(signalType)
                .recoPrice(price)
                .aiScore(aiScore)
                .reason(reason)
                .recoAt(LocalDateTime.now())
                .build();

        Recommendation saved = recommendationRepository.save(recommendation);
        log.info("Saved recommendation: {} {} at {}", signalType, stockCode, price);

        return RecommendationDto.from(saved);
    }

    /**
     * 전체 적중률 조회
     */
    @Transactional(readOnly = true)
    public HitRateDto getOverallHitRate() {
        return recommendationRepository.calculateOverallHitRate();
    }

    /**
     * 기간별 적중률 조회
     */
    @Transactional(readOnly = true)
    public HitRateDto getHitRateByPeriod(LocalDateTime start, LocalDateTime end) {
        return recommendationRepository.calculateHitRateByPeriod(start, end);
    }

    /**
     * 종목별 적중률 조회
     */
    @Transactional(readOnly = true)
    public List<StockHitRateDto> getHitRateByStock() {
        return recommendationRepository.calculateHitRateByStock();
    }

    /**
     * 신호 유형별 적중률 조회 (BUY vs SELL)
     */
    @Transactional(readOnly = true)
    public Map<String, HitRateDto> getHitRateBySignalType() {
        Map<String, HitRateDto> result = new HashMap<>();
        result.put("BUY", recommendationRepository.calculateHitRateBySignalType("BUY"));
        result.put("SELL", recommendationRepository.calculateHitRateBySignalType("SELL"));
        return result;
    }

    /**
     * 최근 추천 목록 조회
     */
    @Transactional(readOnly = true)
    public List<RecommendationDto> getRecentRecommendations(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return recommendationRepository.findRecentRecommendations(since)
                .stream()
                .map(RecommendationDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 스케줄러: 평가 대기 중인 추천 평가 (30분마다)
     */
    @Scheduled(fixedRate = 1800000) // 30분
    @Transactional
    public void evaluatePendingRecommendations() {
        log.info("Starting recommendation evaluation...");

        List<Recommendation> pendingList = recommendationRepository.findByResult(RecoResult.PENDING);

        for (Recommendation reco : pendingList) {
            try {
                Map<String, Object> stockInfo = naverStockService.fetchStock(reco.getStockCode());
                if (stockInfo != null) {
                    BigDecimal currentPrice = new BigDecimal(stockInfo.get("price").toString());
                    reco.evaluate(currentPrice, SUCCESS_THRESHOLD, FAIL_THRESHOLD);
                    log.debug("Evaluated {}: {} -> {} ({}%)",
                            reco.getStockCode(), reco.getResult(),
                            reco.getCurrentPrice(), reco.getProfitRate());
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate recommendation {}: {}",
                        reco.getId(), e.getMessage());
            }
        }

        log.info("Evaluated {} recommendations.", pendingList.size());
    }

    /**
     * 종목별 추천 이력 조회
     */
    @Transactional(readOnly = true)
    public List<RecommendationDto> getRecommendationsByStock(String stockCode) {
        return recommendationRepository.findByStockCodeOrderByRecoAtDesc(stockCode)
                .stream()
                .map(RecommendationDto::from)
                .collect(Collectors.toList());
    }
}
