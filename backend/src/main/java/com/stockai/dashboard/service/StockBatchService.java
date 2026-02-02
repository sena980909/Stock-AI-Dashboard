package com.stockai.dashboard.service;

import com.stockai.dashboard.domain.dto.AiPerformanceDto;
import com.stockai.dashboard.domain.dto.TopStockDto;
import com.stockai.dashboard.domain.entity.RecommendationHistory;
import com.stockai.dashboard.repository.RecommendationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockBatchService {

    private final RecommendationHistoryRepository historyRepository;
    private final StockRankService stockRankService;
    private final NaverStockService naverStockService;

    private static final int MIN_AI_SCORE_FOR_RECOMMENDATION = 70;  // 추천 기준 점수
    private static final int PERFORMANCE_PERIOD_DAYS = 30;          // 성과 집계 기간

    // ========================================
    // 1. 매일 오전 08:50 - 당일 AI 추천 종목 스냅샷 저장
    // ========================================
    @Scheduled(cron = "0 50 8 * * MON-FRI", zone = "Asia/Seoul")
    @Transactional
    public void saveTodayRecommendations() {
        log.info("[Batch] ========== 일일 AI 추천 종목 저장 시작 ==========");
        LocalDate today = LocalDate.now();

        try {
            // AI 추천 Top 종목 조회
            List<TopStockDto> topStocks = stockRankService.getTop10Stocks();

            int savedCount = 0;
            for (TopStockDto stock : topStocks) {
                // AI 점수 기준 미달 시 스킵
                if (stock.getAiScore() == null || stock.getAiScore() < MIN_AI_SCORE_FOR_RECOMMENDATION) {
                    continue;
                }

                // 이미 오늘 저장된 기록이 있으면 스킵
                if (historyRepository.findByStockCodeAndRecoDate(stock.getStockCode(), today).isPresent()) {
                    log.debug("[Batch] Already exists: {} on {}", stock.getStockCode(), today);
                    continue;
                }

                // 현재가 조회
                Long currentPrice = stock.getCurrentPrice();
                if (currentPrice == null || currentPrice == 0) {
                    currentPrice = fetchCurrentPrice(stock.getStockCode());
                }

                if (currentPrice == null || currentPrice == 0) {
                    log.warn("[Batch] Skip {} - price not available", stock.getStockCode());
                    continue;
                }

                // 추천 이유 생성
                String recoReason = generateRecoReason(stock);

                // 엔티티 생성 및 저장
                RecommendationHistory history = RecommendationHistory.builder()
                        .stockCode(stock.getStockCode())
                        .stockName(stock.getStockName())
                        .recoDate(today)
                        .aiScore(stock.getAiScore())
                        .buyPrice(BigDecimal.valueOf(currentPrice))
                        .signalType(stock.getSignalType())
                        .recoReason(recoReason)
                        .build();

                historyRepository.save(history);
                savedCount++;

                log.info("[Batch] Saved recommendation: {} ({}) - AI점수: {}, 매수가: {}",
                        stock.getStockName(), stock.getStockCode(),
                        stock.getAiScore(), currentPrice);
            }

            log.info("[Batch] ========== 일일 추천 저장 완료: {} 건 ==========", savedCount);

        } catch (Exception e) {
            log.error("[Batch] 일일 추천 저장 실패: {}", e.getMessage(), e);
        }
    }

    // ========================================
    // 2. 매일 오후 15:40 - 수익률 계산 및 업데이트
    // ========================================
    @Scheduled(cron = "0 40 15 * * MON-FRI", zone = "Asia/Seoul")
    @Transactional
    public void updatePerformance() {
        log.info("[Batch] ========== 수익률 계산 시작 ==========");
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(PERFORMANCE_PERIOD_DAYS);

        try {
            // 최근 N일간의 추천 기록 조회
            List<RecommendationHistory> records = historyRepository
                    .findByRecoDateBetweenOrderByRecoDateDesc(startDate, today);

            int updatedCount = 0;
            int successCount = 0;
            double totalReturn = 0.0;

            for (RecommendationHistory record : records) {
                try {
                    // 현재가 조회
                    Long currentPrice = fetchCurrentPrice(record.getStockCode());

                    if (currentPrice == null || currentPrice == 0) {
                        log.warn("[Batch] Skip update {} - price not available", record.getStockCode());
                        continue;
                    }

                    // 수익률 계산
                    record.calculatePerformance(BigDecimal.valueOf(currentPrice));

                    historyRepository.save(record);
                    updatedCount++;

                    if (record.getIsSuccess() != null && record.getIsSuccess()) {
                        successCount++;
                    }
                    if (record.getProfitRate() != null) {
                        totalReturn += record.getProfitRate();
                    }

                    log.debug("[Batch] Updated: {} - 매수가: {}, 현재가: {}, 수익률: {}%",
                            record.getStockName(),
                            record.getBuyPrice(),
                            currentPrice,
                            record.getProfitRate());

                } catch (Exception e) {
                    log.warn("[Batch] Failed to update {}: {}", record.getStockCode(), e.getMessage());
                }
            }

            double hitRate = updatedCount > 0 ? (double) successCount / updatedCount * 100 : 0;
            double avgReturn = updatedCount > 0 ? totalReturn / updatedCount : 0;

            log.info("[Batch] ========== 수익률 계산 완료 ==========");
            log.info("[Batch] 업데이트: {} 건, 적중률: {:.1f}%, 평균수익률: {:.2f}%",
                    updatedCount, hitRate, avgReturn);

        } catch (Exception e) {
            log.error("[Batch] 수익률 계산 실패: {}", e.getMessage(), e);
        }
    }

    // ========================================
    // 3. AI 성과 통계 조회
    // ========================================
    @Transactional(readOnly = true)
    public AiPerformanceDto getPerformanceStats() {
        return getPerformanceStats(PERFORMANCE_PERIOD_DAYS);
    }

    @Transactional(readOnly = true)
    public AiPerformanceDto getPerformanceStats(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        log.info("[Performance] Calculating stats for {} days ({} ~ {})", days, startDate, endDate);

        // 기본 통계 조회
        Long totalCount = historyRepository.countTotalEvaluatedRecords(startDate);
        Long successCount = historyRepository.countSuccessRecords(startDate);
        Double avgReturn = historyRepository.getAverageReturn(startDate);

        // 적중률 계산
        Double hitRate = AiPerformanceDto.calculateHitRate(
                successCount != null ? successCount : 0,
                totalCount != null ? totalCount : 0
        );

        // 최고/최저 수익 종목
        AiPerformanceDto.StockPerformance bestStock = null;
        AiPerformanceDto.StockPerformance worstStock = null;

        List<RecommendationHistory> topPerformers = historyRepository.findTopPerformers(startDate);
        if (!topPerformers.isEmpty()) {
            RecommendationHistory best = topPerformers.get(0);
            bestStock = AiPerformanceDto.StockPerformance.builder()
                    .stockCode(best.getStockCode())
                    .stockName(best.getStockName())
                    .returnRate(best.getProfitRate())
                    .recoDate(best.getRecoDate())
                    .aiScore(best.getAiScore())
                    .buyPrice(best.getBuyPrice() != null ? best.getBuyPrice().longValue() : null)
                    .currentPrice(best.getCurrentPrice() != null ? best.getCurrentPrice().longValue() : null)
                    .build();
        }

        List<RecommendationHistory> worstPerformers = historyRepository.findWorstPerformers(startDate);
        if (!worstPerformers.isEmpty()) {
            RecommendationHistory worst = worstPerformers.get(0);
            worstStock = AiPerformanceDto.StockPerformance.builder()
                    .stockCode(worst.getStockCode())
                    .stockName(worst.getStockName())
                    .returnRate(worst.getProfitRate())
                    .recoDate(worst.getRecoDate())
                    .aiScore(worst.getAiScore())
                    .buyPrice(worst.getBuyPrice() != null ? worst.getBuyPrice().longValue() : null)
                    .currentPrice(worst.getCurrentPrice() != null ? worst.getCurrentPrice().longValue() : null)
                    .build();
        }

        // 최근 히스토리 (최대 20건)
        List<RecommendationHistory> recentRecords = historyRepository.findEvaluatedRecords(startDate);
        List<AiPerformanceDto.RecommendationRecord> recentHistory = recentRecords.stream()
                .limit(20)
                .map(AiPerformanceDto.RecommendationRecord::from)
                .collect(Collectors.toList());

        // 일별 통계
        List<Object[]> dailyRaw = historyRepository.getDailyStatistics(startDate);
        List<AiPerformanceDto.DailyStats> dailyStats = dailyRaw.stream()
                .map(row -> AiPerformanceDto.DailyStats.builder()
                        .date((LocalDate) row[0])
                        .totalCount(((Number) row[1]).intValue())
                        .successCount(((Number) row[2]).intValue())
                        .hitRate(AiPerformanceDto.calculateHitRate(
                                ((Number) row[2]).longValue(),
                                ((Number) row[1]).longValue()))
                        .avgReturn(row[3] != null ? ((Number) row[3]).doubleValue() : 0.0)
                        .build())
                .collect(Collectors.toList());

        // 누적 수익률 계산
        Double totalReturn = recentRecords.stream()
                .filter(r -> r.getProfitRate() != null)
                .mapToDouble(RecommendationHistory::getProfitRate)
                .sum();

        // 연속 성공 횟수 계산
        int winStreak = calculateWinStreak(recentRecords);

        return AiPerformanceDto.builder()
                .totalCount(totalCount != null ? totalCount.intValue() : 0)
                .successCount(successCount != null ? successCount.intValue() : 0)
                .hitRate(hitRate)
                .averageReturn(avgReturn != null ? Math.round(avgReturn * 100.0) / 100.0 : 0.0)
                .bestStock(bestStock)
                .worstStock(worstStock)
                .periodDays(days)
                .startDate(startDate)
                .endDate(endDate)
                .totalReturn(totalReturn != null ? Math.round(totalReturn * 100.0) / 100.0 : 0.0)
                .winStreak(winStreak)
                .recentHistory(recentHistory)
                .dailyStats(dailyStats)
                .build();
    }

    // ========================================
    // 수동 실행 메소드 (테스트/관리용)
    // ========================================

    /**
     * 수동으로 오늘의 추천 저장 실행
     */
    @Transactional
    public int saveRecommendationsManual() {
        log.info("[Manual] 수동 추천 저장 시작");
        saveTodayRecommendations();

        LocalDate today = LocalDate.now();
        return historyRepository.findByRecoDate(today).size();
    }

    /**
     * 수동으로 수익률 업데이트 실행
     */
    @Transactional
    public void updatePerformanceManual() {
        log.info("[Manual] 수동 수익률 업데이트 시작");
        updatePerformance();
    }

    /**
     * 테스트용 샘플 데이터 생성
     */
    @Transactional
    public void generateSampleData(int days) {
        log.info("[Sample] 샘플 데이터 생성 시작: {} 일치", days);

        List<TopStockDto> topStocks = stockRankService.getTop10Stocks();
        Random random = new Random();

        for (int i = 0; i < days; i++) {
            LocalDate date = LocalDate.now().minusDays(i + 1);

            // 각 날짜에 3~5개 종목 추천
            int recoCount = 3 + random.nextInt(3);
            Collections.shuffle(topStocks);

            for (int j = 0; j < Math.min(recoCount, topStocks.size()); j++) {
                TopStockDto stock = topStocks.get(j);

                if (historyRepository.findByStockCodeAndRecoDate(stock.getStockCode(), date).isPresent()) {
                    continue;
                }

                Long basePrice = stock.getCurrentPrice() != null ? stock.getCurrentPrice() : 50000L;
                // 과거 가격은 현재가 대비 -10% ~ +10% 변동
                double priceVariation = 1 + (random.nextDouble() - 0.5) * 0.2;
                Long buyPrice = (long) (basePrice * priceVariation);

                // 수익률: -8% ~ +15% (양수 확률이 높게)
                double profitRate = -8 + random.nextDouble() * 23;
                Long currentPrice = (long) (buyPrice * (1 + profitRate / 100));

                int aiScore = 70 + random.nextInt(20);

                RecommendationHistory history = RecommendationHistory.builder()
                        .stockCode(stock.getStockCode())
                        .stockName(stock.getStockName())
                        .recoDate(date)
                        .aiScore(aiScore)
                        .buyPrice(BigDecimal.valueOf(buyPrice))
                        .currentPrice(BigDecimal.valueOf(currentPrice))
                        .profitRate(Math.round(profitRate * 100.0) / 100.0)
                        .isSuccess(profitRate > 0)
                        .signalType(aiScore >= 80 ? "STRONG_BUY" : "BUY")
                        .recoReason(generateRecoReason(stock))
                        .build();

                historyRepository.save(history);
            }
        }

        log.info("[Sample] 샘플 데이터 생성 완료");
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    private Long fetchCurrentPrice(String stockCode) {
        try {
            Map<String, Object> stockData = naverStockService.fetchStock(stockCode);
            if (stockData != null && stockData.get("price") != null) {
                return ((Number) stockData.get("price")).longValue();
            }
        } catch (Exception e) {
            log.warn("[Batch] Failed to fetch price for {}: {}", stockCode, e.getMessage());
        }
        return null;
    }

    private String generateRecoReason(TopStockDto stock) {
        if (stock.getAiScore() == null) return "AI 분석 기반 매수 추천";

        int score = stock.getAiScore();
        if (score >= 85) {
            return "AI 고점수 + 기술적 지표 강세, 적극 매수 추천";
        } else if (score >= 80) {
            return "AI 점수 상위권, 외국인 수급 양호";
        } else if (score >= 75) {
            return "기술적 반등 신호, 분할 매수 추천";
        } else {
            return "업종 대비 상대적 강세 예상";
        }
    }

    private int calculateWinStreak(List<RecommendationHistory> records) {
        int streak = 0;
        for (RecommendationHistory record : records) {
            if (record.getIsSuccess() != null && record.getIsSuccess()) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }
}
