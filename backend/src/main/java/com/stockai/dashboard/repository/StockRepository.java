package com.stockai.dashboard.repository;

import com.stockai.dashboard.domain.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findBySymbol(String symbol);

    List<Stock> findBySymbolContainingOrNameContaining(String symbol, String name);

    List<Stock> findByMarket(String market);

    List<Stock> findByIsActiveTrue();
}
