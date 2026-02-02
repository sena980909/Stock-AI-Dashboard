package com.stockai.dashboard.repository;

import com.stockai.dashboard.domain.entity.StockAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockAnalysisRepository extends JpaRepository<StockAnalysis, Long> {

    /**
     * 종목 코드로 최신 분석 조회
     */
    Optional<StockAnalysis> findFirstByStockCodeOrderByUpdatedAtDesc(String stockCode);

    /**
     * 시가총액 상위 N개 종목 분석 조회
     */
    @Query("SELECT sa FROM StockAnalysis sa " +
            "WHERE sa.marketCap IS NOT NULL " +
            "ORDER BY sa.marketCap DESC")
    List<StockAnalysis> findTopByMarketCap(@Param("limit") int limit);

    /**
     * 시가총액 상위 10개 종목 조회 (Native Query)
     */
    @Query(value = "SELECT * FROM stock_analysis " +
            "WHERE market_cap IS NOT NULL " +
            "ORDER BY market_cap DESC " +
            "LIMIT 10", nativeQuery = true)
    List<StockAnalysis> findTop10ByMarketCapDesc();

    /**
     * AI 점수 상위 N개 종목 조회
     */
    @Query("SELECT sa FROM StockAnalysis sa " +
            "WHERE sa.totalScore IS NOT NULL " +
            "ORDER BY sa.totalScore DESC")
    List<StockAnalysis> findTopByTotalScore(@Param("limit") int limit);

    /**
     * 특정 종목 코드 목록의 분석 데이터 조회
     */
    @Query("SELECT sa FROM StockAnalysis sa " +
            "WHERE sa.stockCode IN :stockCodes")
    List<StockAnalysis> findByStockCodeIn(@Param("stockCodes") List<String> stockCodes);

    /**
     * 특정 시간 이후 업데이트된 분석 조회
     */
    List<StockAnalysis> findByUpdatedAtAfter(LocalDateTime since);

    /**
     * 종목 코드로 분석 존재 여부 확인
     */
    boolean existsByStockCode(String stockCode);

    /**
     * 종목명으로 검색 (부분 일치)
     */
    @Query("SELECT sa FROM StockAnalysis sa " +
            "WHERE sa.stockName LIKE %:keyword% OR sa.stockCode LIKE %:keyword%")
    List<StockAnalysis> searchByKeyword(@Param("keyword") String keyword);

    /**
     * 시그널 타입별 조회
     */
    List<StockAnalysis> findBySignalType(String signalType);
}
