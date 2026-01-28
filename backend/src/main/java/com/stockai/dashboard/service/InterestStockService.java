package com.stockai.dashboard.service;

import com.stockai.dashboard.domain.dto.InterestStockDto;
import com.stockai.dashboard.domain.entity.InterestStock;
import com.stockai.dashboard.repository.InterestStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterestStockService {

    private final InterestStockRepository interestStockRepository;
    private final NaverStockService naverStockService;

    private static final int MAX_INTEREST_STOCKS = 20;

    /**
     * 관심 종목 목록 조회 (실시간 정보 포함)
     */
    @Transactional(readOnly = true)
    public List<InterestStockDto> getInterestStocks(Long userId) {
        List<InterestStock> interests = interestStockRepository
                .findByUserIdOrderByDisplayOrderAsc(userId);

        return interests.stream()
                .map(interest -> {
                    InterestStockDto dto = InterestStockDto.from(interest);

                    // 실시간 가격 정보 추가
                    try {
                        Map<String, Object> stockInfo = naverStockService.fetchStock(interest.getStockCode());
                        if (stockInfo != null) {
                            dto.setCurrentPrice(new BigDecimal(stockInfo.get("price").toString()));
                            dto.setChange(new BigDecimal(stockInfo.get("change").toString()));
                            dto.setChangePercent(new BigDecimal(stockInfo.get("changePercent").toString()));
                            dto.setAiScore((Integer) stockInfo.get("aiScore"));
                            dto.setSentiment((String) stockInfo.get("sentiment"));
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch real-time info for {}", interest.getStockCode());
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 관심 종목 등록
     */
    @Transactional
    public InterestStockDto addInterestStock(Long userId, InterestStockDto.CreateRequest request) {
        // 중복 확인
        if (interestStockRepository.existsByUserIdAndStockCode(userId, request.getStockCode())) {
            throw new IllegalArgumentException("이미 관심 종목에 등록되어 있습니다.");
        }

        // 최대 개수 확인
        long count = interestStockRepository.countByUserId(userId);
        if (count >= MAX_INTEREST_STOCKS) {
            throw new IllegalArgumentException("관심 종목은 최대 " + MAX_INTEREST_STOCKS + "개까지 등록 가능합니다.");
        }

        // 순서 결정
        Integer maxOrder = interestStockRepository.findMaxDisplayOrderByUserId(userId);

        InterestStock interestStock = InterestStock.builder()
                .userId(userId)
                .stockCode(request.getStockCode())
                .stockName(request.getStockName())
                .memo(request.getMemo())
                .displayOrder(maxOrder + 1)
                .build();

        InterestStock saved = interestStockRepository.save(interestStock);
        log.info("Added interest stock: userId={}, stockCode={}", userId, request.getStockCode());

        return InterestStockDto.from(saved);
    }

    /**
     * 관심 종목 삭제
     */
    @Transactional
    public void removeInterestStock(Long userId, String stockCode) {
        interestStockRepository.deleteByUserIdAndStockCode(userId, stockCode);
        log.info("Removed interest stock: userId={}, stockCode={}", userId, stockCode);
    }

    /**
     * 관심 종목 수정 (순서, 메모)
     */
    @Transactional
    public InterestStockDto updateInterestStock(Long userId, String stockCode,
                                                 InterestStockDto.UpdateRequest request) {
        InterestStock interest = interestStockRepository.findByUserIdAndStockCode(userId, stockCode)
                .orElseThrow(() -> new IllegalArgumentException("관심 종목을 찾을 수 없습니다."));

        if (request.getDisplayOrder() != null) {
            interest.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getMemo() != null) {
            interest.setMemo(request.getMemo());
        }

        return InterestStockDto.from(interest);
    }

    /**
     * 관심 종목 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean isInterested(Long userId, String stockCode) {
        return interestStockRepository.existsByUserIdAndStockCode(userId, stockCode);
    }
}
