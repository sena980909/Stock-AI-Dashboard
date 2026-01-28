package com.stockai.dashboard.domain.dto;

import com.stockai.dashboard.domain.entity.StockHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 일봉 데이터 DTO (프론트엔드 차트 라이브러리 호환)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockHistoryDto {

    private String timestamp;       // ISO 8601 형식 (차트 라이브러리용)
    private LocalDate date;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private Long volume;
    private BigDecimal change;      // 등락액
    private BigDecimal changePercent; // 등락률

    public static StockHistoryDto from(StockHistory entity) {
        return StockHistoryDto.builder()
                .timestamp(entity.getTradeDate().toString())
                .date(entity.getTradeDate())
                .open(entity.getOpenPrice())
                .high(entity.getHighPrice())
                .low(entity.getLowPrice())
                .close(entity.getClosePrice())
                .volume(entity.getVolume())
                .build();
    }

    public static StockHistoryDto from(StockHistory entity, StockHistory previous) {
        StockHistoryDto dto = from(entity);
        if (previous != null && previous.getClosePrice() != null) {
            dto.setChange(entity.getClosePrice().subtract(previous.getClosePrice()));
            dto.setChangePercent(entity.calculateChangePercent(previous.getClosePrice()));
        }
        return dto;
    }
}
