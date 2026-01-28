package com.stockai.dashboard.repository;

import com.stockai.dashboard.domain.entity.StockHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockHistoryRepository extends JpaRepository<StockHistory, Long> {

    /**
     * 종목 코드와 날짜 범위로 일봉 데이터 조회
     */
    List<StockHistory> findByStockCodeAndTradeDateBetweenOrderByTradeDateAsc(
            String stockCode, LocalDate startDate, LocalDate endDate);

    /**
     * 최근 N일간 일봉 데이터 조회
     */
    @Query("SELECT sh FROM StockHistory sh WHERE sh.stockCode = :stockCode " +
            "AND sh.tradeDate >= :startDate ORDER BY sh.tradeDate ASC")
    List<StockHistory> findRecentHistory(
            @Param("stockCode") String stockCode,
            @Param("startDate") LocalDate startDate);

    /**
     * 특정 종목의 최신 일봉 데이터 조회
     */
    Optional<StockHistory> findFirstByStockCodeOrderByTradeDateDesc(String stockCode);

    /**
     * 특정 날짜의 일봉 데이터 존재 여부 확인
     */
    boolean existsByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);

    /**
     * 특정 종목의 특정 날짜 일봉 조회
     */
    Optional<StockHistory> findByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);
}
