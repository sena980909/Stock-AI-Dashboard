package com.stockai.dashboard.domain.dto;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockSearchResultDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String code;
    private String name;
    private String market;
    private Long currentPrice;
    private Double changeRate;
    private Long marketCap;
    private Integer aiScore;
    private String signalType;

    /**
     * 시가총액 포맷 (조/억 단위)
     */
    public String getFormattedMarketCap() {
        if (marketCap == null || marketCap == 0) return "-";

        if (marketCap >= 1_000_000_000_000L) {
            return String.format("%.1f조", marketCap / 1_000_000_000_000.0);
        } else if (marketCap >= 100_000_000L) {
            return String.format("%.0f억", marketCap / 100_000_000.0);
        }
        return String.format("%,d원", marketCap);
    }

    /**
     * 가격 포맷
     */
    public String getFormattedPrice() {
        if (currentPrice == null) return "-";
        return String.format("%,d원", currentPrice);
    }
}
