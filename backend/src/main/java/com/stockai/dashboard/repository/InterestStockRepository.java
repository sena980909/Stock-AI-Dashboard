package com.stockai.dashboard.repository;

import com.stockai.dashboard.domain.entity.InterestStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterestStockRepository extends JpaRepository<InterestStock, Long> {

    /**
     * 사용자의 관심 종목 목록 조회 (순서대로)
     */
    List<InterestStock> findByUserIdOrderByDisplayOrderAsc(Long userId);

    /**
     * 특정 관심 종목 조회
     */
    Optional<InterestStock> findByUserIdAndStockCode(Long userId, String stockCode);

    /**
     * 관심 종목 등록 여부 확인
     */
    boolean existsByUserIdAndStockCode(Long userId, String stockCode);

    /**
     * 관심 종목 삭제
     */
    @Modifying
    @Query("DELETE FROM InterestStock i WHERE i.userId = :userId AND i.stockCode = :stockCode")
    void deleteByUserIdAndStockCode(@Param("userId") Long userId, @Param("stockCode") String stockCode);

    /**
     * 사용자의 관심 종목 수
     */
    long countByUserId(Long userId);

    /**
     * 사용자의 최대 표시 순서 조회
     */
    @Query("SELECT COALESCE(MAX(i.displayOrder), 0) FROM InterestStock i WHERE i.userId = :userId")
    Integer findMaxDisplayOrderByUserId(@Param("userId") Long userId);
}
