package com.stockai.dashboard.repository;

import com.stockai.dashboard.domain.entity.Stock;
import com.stockai.dashboard.domain.entity.StockPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {

    List<StockPrice> findByStockAndTradeDateBetweenOrderByTradeDateDesc(
            Stock stock, LocalDate startDate, LocalDate endDate);

    @Query("SELECT sp FROM StockPrice sp WHERE sp.stock.symbol = :symbol " +
            "AND sp.tradeDate BETWEEN :startDate AND :endDate ORDER BY sp.tradeDate DESC")
    List<StockPrice> findBySymbolAndDateRange(
            @Param("symbol") String symbol,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT sp FROM StockPrice sp WHERE sp.stock = :stock ORDER BY sp.tradeDate DESC LIMIT 1")
    StockPrice findLatestByStock(@Param("stock") Stock stock);
}
