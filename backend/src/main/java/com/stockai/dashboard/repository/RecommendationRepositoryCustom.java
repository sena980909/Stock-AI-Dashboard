package com.stockai.dashboard.repository;

import com.stockai.dashboard.domain.dto.HitRateDto;
import com.stockai.dashboard.domain.dto.StockHitRateDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * QueryDSL을 사용한 적중률 분석 Custom Repository
 */
public interface RecommendationRepositoryCustom {

    /**
     * 전체 적중률 계산
     */
    HitRateDto calculateOverallHitRate();

    /**
     * 기간별 적중률 계산
     */
    HitRateDto calculateHitRateByPeriod(LocalDateTime start, LocalDateTime end);

    /**
     * 종목별 적중률 계산
     */
    List<StockHitRateDto> calculateHitRateByStock();

    /**
     * 신호 유형별 적중률 (BUY vs SELL)
     */
    HitRateDto calculateHitRateBySignalType(String signalType);
}
