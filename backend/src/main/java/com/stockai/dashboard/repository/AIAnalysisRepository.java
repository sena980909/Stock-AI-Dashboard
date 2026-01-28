package com.stockai.dashboard.repository;

import com.stockai.dashboard.domain.entity.AIAnalysis;
import com.stockai.dashboard.domain.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AIAnalysisRepository extends JpaRepository<AIAnalysis, Long> {

    @Query("SELECT a FROM AIAnalysis a WHERE a.stock = :stock ORDER BY a.analyzedAt DESC LIMIT 1")
    Optional<AIAnalysis> findLatestByStock(@Param("stock") Stock stock);

    @Query("SELECT a FROM AIAnalysis a WHERE a.stock.symbol = :symbol ORDER BY a.analyzedAt DESC LIMIT 1")
    Optional<AIAnalysis> findLatestBySymbol(@Param("symbol") String symbol);

    @Query("SELECT a FROM AIAnalysis a WHERE a.aiScore >= :minScore ORDER BY a.aiScore DESC")
    List<AIAnalysis> findByMinScore(@Param("minScore") Integer minScore);

    List<AIAnalysis> findByRecommendation(String recommendation);
}
