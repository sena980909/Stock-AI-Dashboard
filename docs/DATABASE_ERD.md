# Stock AI Dashboard - 데이터베이스 ERD

## ERD 다이어그램 (Mermaid)

```mermaid
erDiagram
    USERS {
        bigint id PK "사용자 ID"
        varchar(100) email UK "이메일 (로그인용)"
        varchar(255) password "암호화된 비밀번호"
        varchar(50) name "사용자 이름"
        varchar(20) role "권한: USER, ADMIN"
        timestamp created_at "생성일시"
        timestamp updated_at "수정일시"
        boolean is_active "활성 상태"
    }

    STOCKS {
        bigint id PK "종목 ID"
        varchar(20) symbol UK "종목 코드"
        varchar(100) name "종목명"
        varchar(50) market "시장: KOSPI, KOSDAQ"
        varchar(100) sector "업종"
        bigint market_cap "시가총액"
        boolean is_active "거래 가능 여부"
        timestamp created_at "생성일시"
        timestamp updated_at "수정일시"
    }

    STOCK_HISTORY {
        bigint id PK "이력 ID"
        varchar(20) stock_code "종목 코드"
        date trade_date "거래일"
        decimal(15_2) open_price "시가"
        decimal(15_2) high_price "고가"
        decimal(15_2) low_price "저가"
        decimal(15_2) close_price "종가"
        bigint volume "거래량"
        timestamp created_at "생성일시"
    }

    INTEREST_STOCK {
        bigint id PK "관심 ID"
        bigint user_id FK "사용자 ID"
        varchar(20) stock_code "종목 코드"
        varchar(100) stock_name "종목명"
        integer display_order "표시 순서"
        varchar(100) memo "메모"
        timestamp created_at "등록일시"
    }

    RECOMMENDATION {
        bigint id PK "추천 ID"
        varchar(20) stock_code "종목 코드"
        varchar(100) stock_name "종목명"
        varchar(10) signal_type "신호: BUY, SELL"
        decimal(15_2) reco_price "추천 시점 가격"
        integer ai_score "AI 점수"
        text reason "추천 근거"
        decimal(15_2) current_price "현재 가격 (업데이트)"
        decimal(5_2) profit_rate "수익률"
        varchar(10) result "결과: SUCCESS, FAIL, PENDING"
        timestamp reco_at "추천일시"
        timestamp evaluated_at "평가일시"
    }

    AI_ANALYSIS {
        bigint id PK "분석 ID"
        varchar(20) stock_code "종목 코드"
        integer ai_score "AI 점수 (0-100)"
        varchar(10) recommendation "추천: BUY, SELL, HOLD"
        text reasons "분석 근거 (JSON)"
        integer positive_news_count "긍정 뉴스 수"
        integer negative_news_count "부정 뉴스 수"
        integer neutral_news_count "중립 뉴스 수"
        timestamp analyzed_at "분석일시"
    }

    NEWS {
        bigint id PK "뉴스 ID"
        varchar(500) title "제목"
        text content "본문"
        text summary "요약"
        varchar(20) sentiment "감성: POSITIVE, NEGATIVE, NEUTRAL"
        decimal(3_2) sentiment_score "감성 점수 (-1 ~ 1)"
        varchar(100) source "출처"
        varchar(500) url "원문 URL"
        timestamp published_at "발행일시"
        timestamp created_at "수집일시"
    }

    NEWS_STOCKS {
        bigint id PK "관계 ID"
        bigint news_id FK "뉴스 ID"
        varchar(20) stock_code "종목 코드"
        decimal(3_2) relevance_score "연관도 점수"
    }

    %% 관계 정의
    USERS ||--o{ INTEREST_STOCK : "관심종목등록"

    STOCKS ||--o{ AI_ANALYSIS : "AI분석"

    NEWS ||--o{ NEWS_STOCKS : "관련종목"
```

## 신규 테이블 상세 설명

### STOCK_HISTORY (일봉 데이터)
- 일별 OHLCV(시가, 고가, 저가, 종가, 거래량) 데이터 저장
- 캔들 차트 렌더링에 사용
- **복합 인덱스**: `(stock_code, trade_date)` - 빠른 조회를 위해

### INTEREST_STOCK (관심 종목)
- 사용자별 관심 종목 저장
- `memo` 필드로 개인 메모 가능
- `display_order`로 순서 커스터마이징

### RECOMMENDATION (AI 추천 기록)
- AI가 추천한 종목과 추천 시점 가격 저장
- `profit_rate`: 현재가 대비 수익률
- `result`: 성공(+3% 이상), 실패(-3% 이하), 대기(평가 중)
- **적중률 분석**에 핵심 테이블

## 인덱스 전략

```sql
-- 일봉 데이터 조회 최적화 (복합 인덱스)
CREATE UNIQUE INDEX idx_stock_history_code_date
    ON stock_history(stock_code, trade_date DESC);

-- 관심 종목 조회
CREATE INDEX idx_interest_stock_user
    ON interest_stock(user_id, display_order);

-- AI 추천 적중률 분석
CREATE INDEX idx_recommendation_code_date
    ON recommendation(stock_code, reco_at DESC);
CREATE INDEX idx_recommendation_result
    ON recommendation(result, reco_at DESC);

-- AI 분석 조회
CREATE INDEX idx_ai_analysis_code_date
    ON ai_analysis(stock_code, analyzed_at DESC);
```

## 적중률 계산 로직

```sql
-- 전체 적중률
SELECT
    COUNT(*) as total_count,
    SUM(CASE WHEN result = 'SUCCESS' THEN 1 ELSE 0 END) as success_count,
    ROUND(SUM(CASE WHEN result = 'SUCCESS' THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) as hit_rate
FROM recommendation
WHERE result != 'PENDING';

-- 종목별 적중률
SELECT
    stock_code,
    stock_name,
    COUNT(*) as total_count,
    SUM(CASE WHEN result = 'SUCCESS' THEN 1 ELSE 0 END) as success_count,
    ROUND(AVG(profit_rate), 2) as avg_profit_rate
FROM recommendation
WHERE result != 'PENDING'
GROUP BY stock_code, stock_name
ORDER BY success_count DESC;
```
