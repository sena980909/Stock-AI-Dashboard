package com.stockai.dashboard.repository;

import com.stockai.dashboard.domain.entity.RecommendationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecommendationHistoryRepository extends JpaRepository<RecommendationHistory, Long> {

    /**
     * 특정 날짜의 추천 종목 조회
     */
    List<RecommendationHistory> findByRecoDate(LocalDate recoDate);

    /**
     * 특정 종목의 특정 날짜 추천 기록 조회
     */
    Optional<RecommendationHistory> findByStockCodeAndRecoDate(String stockCode, LocalDate recoDate);

    /**
     * 기간 내 추천 기록 조회 (최신순)
     */
    List<RecommendationHistory> findByRecoDateBetweenOrderByRecoDateDesc(LocalDate startDate, LocalDate endDate);

    /**
     * 수익률이 계산된 기록만 조회 (최근 N일)
     */
    @Query("SELECT r FROM RecommendationHistory r " +
           "WHERE r.recoDate >= :startDate AND r.profitRate IS NOT NULL " +
           "ORDER BY r.recoDate DESC, r.profitRate DESC")
    List<RecommendationHistory> findEvaluatedRecords(@Param("startDate") LocalDate startDate);

    /**
     * 최근 N일간 성공 건수 집계
     */
    @Query("SELECT COUNT(r) FROM RecommendationHistory r " +
           "WHERE r.recoDate >= :startDate AND r.isSuccess = true")
    Long countSuccessRecords(@Param("startDate") LocalDate startDate);

    /**
     * 최근 N일간 전체 건수 집계 (수익률 계산 완료된 것만)
     */
    @Query("SELECT COUNT(r) FROM RecommendationHistory r " +
           "WHERE r.recoDate >= :startDate AND r.profitRate IS NOT NULL")
    Long countTotalEvaluatedRecords(@Param("startDate") LocalDate startDate);

    /**
     * 최근 N일간 평균 수익률
     */
    @Query("SELECT AVG(r.profitRate) FROM RecommendationHistory r " +
           "WHERE r.recoDate >= :startDate AND r.profitRate IS NOT NULL")
    Double getAverageReturn(@Param("startDate") LocalDate startDate);

    /**
     * 최근 N일간 최고 수익 종목
     */
    @Query("SELECT r FROM RecommendationHistory r " +
           "WHERE r.recoDate >= :startDate AND r.profitRate IS NOT NULL " +
           "ORDER BY r.profitRate DESC")
    List<RecommendationHistory> findTopPerformers(@Param("startDate") LocalDate startDate);

    /**
     * 최근 N일간 최저 수익 종목
     */
    @Query("SELECT r FROM RecommendationHistory r " +
           "WHERE r.recoDate >= :startDate AND r.profitRate IS NOT NULL " +
           "ORDER BY r.profitRate ASC")
    List<RecommendationHistory> findWorstPerformers(@Param("startDate") LocalDate startDate);

    /**
     * 특정 종목의 추천 히스토리
     */
    List<RecommendationHistory> findByStockCodeOrderByRecoDateDesc(String stockCode);

    /**
     * 아직 수익률 계산이 안 된 기록 조회 (업데이트 대상)
     */
    @Query("SELECT r FROM RecommendationHistory r " +
           "WHERE r.currentPrice IS NULL OR r.recoDate < :today")
    List<RecommendationHistory> findRecordsToUpdate(@Param("today") LocalDate today);

    /**
     * 특정 기간의 일별 적중률 통계
     */
    @Query("SELECT r.recoDate, " +
           "COUNT(r), " +
           "SUM(CASE WHEN r.isSuccess = true THEN 1 ELSE 0 END), " +
           "AVG(r.profitRate) " +
           "FROM RecommendationHistory r " +
           "WHERE r.recoDate >= :startDate AND r.profitRate IS NOT NULL " +
           "GROUP BY r.recoDate " +
           "ORDER BY r.recoDate DESC")
    List<Object[]> getDailyStatistics(@Param("startDate") LocalDate startDate);
}
