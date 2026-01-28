package com.stockai.dashboard.controller;

import com.stockai.dashboard.domain.dto.InterestStockDto;
import com.stockai.dashboard.service.InterestStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 관심 종목 API Controller
 */
@RestController
@RequestMapping("/api/interests")
@RequiredArgsConstructor
public class InterestStockController {

    private final InterestStockService interestStockService;

    // TODO: JWT에서 userId 추출 (현재는 임시로 1L 사용)
    private Long getCurrentUserId() {
        return 1L;
    }

    /**
     * 관심 종목 목록 조회
     * GET /api/interests
     */
    @GetMapping
    public ResponseEntity<List<InterestStockDto>> getInterestStocks() {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(interestStockService.getInterestStocks(userId));
    }

    /**
     * 관심 종목 등록
     * POST /api/interests
     */
    @PostMapping
    public ResponseEntity<InterestStockDto> addInterestStock(
            @RequestBody InterestStockDto.CreateRequest request) {
        Long userId = getCurrentUserId();
        InterestStockDto result = interestStockService.addInterestStock(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * 관심 종목 삭제
     * DELETE /api/interests/{stockCode}
     */
    @DeleteMapping("/{stockCode}")
    public ResponseEntity<Void> removeInterestStock(@PathVariable String stockCode) {
        Long userId = getCurrentUserId();
        interestStockService.removeInterestStock(userId, stockCode);
        return ResponseEntity.noContent().build();
    }

    /**
     * 관심 종목 수정
     * PATCH /api/interests/{stockCode}
     */
    @PatchMapping("/{stockCode}")
    public ResponseEntity<InterestStockDto> updateInterestStock(
            @PathVariable String stockCode,
            @RequestBody InterestStockDto.UpdateRequest request) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(interestStockService.updateInterestStock(userId, stockCode, request));
    }

    /**
     * 관심 종목 여부 확인
     * GET /api/interests/check/{stockCode}
     */
    @GetMapping("/check/{stockCode}")
    public ResponseEntity<Map<String, Boolean>> checkInterest(@PathVariable String stockCode) {
        Long userId = getCurrentUserId();
        boolean isInterested = interestStockService.isInterested(userId, stockCode);
        return ResponseEntity.ok(Map.of("interested", isInterested));
    }
}
