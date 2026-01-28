package com.stockai.dashboard.domain.dto;

import com.stockai.dashboard.domain.entity.InterestStock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 관심 종목 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterestStockDto {

    private Long id;
    private String stockCode;
    private String stockName;
    private Integer displayOrder;
    private String memo;
    private LocalDateTime createdAt;

    // 실시간 정보 (조회 시 추가)
    private BigDecimal currentPrice;
    private BigDecimal change;
    private BigDecimal changePercent;
    private Integer aiScore;
    private String sentiment;

    public static InterestStockDto from(InterestStock entity) {
        return InterestStockDto.builder()
                .id(entity.getId())
                .stockCode(entity.getStockCode())
                .stockName(entity.getStockName())
                .displayOrder(entity.getDisplayOrder())
                .memo(entity.getMemo())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * 관심 종목 등록 요청 DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private String stockCode;
        private String stockName;
        private String memo;
    }

    /**
     * 관심 종목 수정 요청 DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private Integer displayOrder;
        private String memo;
    }
}
