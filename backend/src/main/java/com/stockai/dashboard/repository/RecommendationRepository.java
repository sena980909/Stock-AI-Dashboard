package com.stockai.dashboard.repository;

import com.stockai.dashboard.domain.entity.Recommendation;
import com.stockai.dashboard.domain.entity.Recommendation.RecoResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long>, RecommendationRepositoryCustom {

    /**
     * 종목별 추천 이력 조회
     */
    List<Recommendation> findByStockCodeOrderByRecoAtDesc(String stockCode);

    /**
     * 특정 결과의 추천 목록 조회
     */
    List<Recommendation> findByResultOrderByRecoAtDesc(RecoResult result);

    /**
     * 평가 대기 중인 추천 목록 (배치용)
     */
    List<Recommendation> findByResult(RecoResult result);

    /**
     * 최근 추천 목록
     */
    @Query("SELECT r FROM Recommendation r WHERE r.recoAt >= :since ORDER BY r.recoAt DESC")
    List<Recommendation> findRecentRecommendations(@Param("since") LocalDateTime since);

    /**
     * 기간별 추천 목록
     */
    List<Recommendation> findByRecoAtBetweenOrderByRecoAtDesc(LocalDateTime start, LocalDateTime end);
}
