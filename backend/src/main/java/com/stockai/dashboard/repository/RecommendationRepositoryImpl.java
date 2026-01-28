package com.stockai.dashboard.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.stockai.dashboard.domain.dto.HitRateDto;
import com.stockai.dashboard.domain.dto.StockHitRateDto;
import com.stockai.dashboard.domain.entity.QRecommendation;
import com.stockai.dashboard.domain.entity.Recommendation.RecoResult;
import com.stockai.dashboard.domain.entity.Recommendation.SignalType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * QueryDSL을 사용한 적중률 분석 구현체
 */
@Repository
@RequiredArgsConstructor
public class RecommendationRepositoryImpl implements RecommendationRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QRecommendation recommendation = QRecommendation.recommendation;

    @Override
    public HitRateDto calculateOverallHitRate() {
        return queryFactory
                .select(Projections.constructor(HitRateDto.class,
                        recommendation.count(),
                        new CaseBuilder()
                                .when(recommendation.result.eq(RecoResult.SUCCESS))
                                .then(1L)
                                .otherwise(0L)
                                .sum(),
                        new CaseBuilder()
                                .when(recommendation.result.eq(RecoResult.FAIL))
                                .then(1L)
                                .otherwise(0L)
                                .sum(),
                        recommendation.profitRate.avg()
                ))
                .from(recommendation)
                .where(recommendation.result.ne(RecoResult.PENDING))
                .fetchOne();
    }

    @Override
    public HitRateDto calculateHitRateByPeriod(LocalDateTime start, LocalDateTime end) {
        return queryFactory
                .select(Projections.constructor(HitRateDto.class,
                        recommendation.count(),
                        new CaseBuilder()
                                .when(recommendation.result.eq(RecoResult.SUCCESS))
                                .then(1L)
                                .otherwise(0L)
                                .sum(),
                        new CaseBuilder()
                                .when(recommendation.result.eq(RecoResult.FAIL))
                                .then(1L)
                                .otherwise(0L)
                                .sum(),
                        recommendation.profitRate.avg()
                ))
                .from(recommendation)
                .where(recommendation.result.ne(RecoResult.PENDING)
                        .and(recommendation.recoAt.between(start, end)))
                .fetchOne();
    }

    @Override
    public List<StockHitRateDto> calculateHitRateByStock() {
        return queryFactory
                .select(Projections.constructor(StockHitRateDto.class,
                        recommendation.stockCode,
                        recommendation.stockName,
                        recommendation.count(),
                        new CaseBuilder()
                                .when(recommendation.result.eq(RecoResult.SUCCESS))
                                .then(1L)
                                .otherwise(0L)
                                .sum(),
                        new CaseBuilder()
                                .when(recommendation.result.eq(RecoResult.FAIL))
                                .then(1L)
                                .otherwise(0L)
                                .sum(),
                        recommendation.profitRate.avg()
                ))
                .from(recommendation)
                .where(recommendation.result.ne(RecoResult.PENDING))
                .groupBy(recommendation.stockCode, recommendation.stockName)
                .orderBy(new CaseBuilder()
                        .when(recommendation.result.eq(RecoResult.SUCCESS))
                        .then(1L)
                        .otherwise(0L)
                        .sum().desc())
                .fetch();
    }

    @Override
    public HitRateDto calculateHitRateBySignalType(String signalType) {
        SignalType type = SignalType.valueOf(signalType.toUpperCase());

        return queryFactory
                .select(Projections.constructor(HitRateDto.class,
                        recommendation.count(),
                        new CaseBuilder()
                                .when(recommendation.result.eq(RecoResult.SUCCESS))
                                .then(1L)
                                .otherwise(0L)
                                .sum(),
                        new CaseBuilder()
                                .when(recommendation.result.eq(RecoResult.FAIL))
                                .then(1L)
                                .otherwise(0L)
                                .sum(),
                        recommendation.profitRate.avg()
                ))
                .from(recommendation)
                .where(recommendation.result.ne(RecoResult.PENDING)
                        .and(recommendation.signalType.eq(type)))
                .fetchOne();
    }
}
