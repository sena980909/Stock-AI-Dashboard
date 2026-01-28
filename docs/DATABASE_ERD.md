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

    STOCK_PRICES {
        bigint id PK "가격 ID"
        bigint stock_id FK "종목 ID"
        decimal(15_2) open_price "시가"
        decimal(15_2) high_price "고가"
        decimal(15_2) low_price "저가"
        decimal(15_2) close_price "종가"
        bigint volume "거래량"
        date trade_date "거래일"
        timestamp recorded_at "기록일시"
    }

    STOCK_REALTIME {
        bigint id PK "실시간 ID"
        bigint stock_id FK "종목 ID"
        decimal(15_2) current_price "현재가"
        decimal(15_2) change_amount "등락액"
        decimal(5_2) change_percent "등락률"
        bigint volume "누적 거래량"
        timestamp updated_at "갱신일시"
    }

    AI_ANALYSIS {
        bigint id PK "분석 ID"
        bigint stock_id FK "종목 ID"
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
        bigint stock_id FK "종목 ID"
        decimal(3_2) relevance_score "연관도 점수"
    }

    ALERTS {
        bigint id PK "알림 ID"
        bigint user_id FK "사용자 ID"
        bigint stock_id FK "종목 ID"
        varchar(10) alert_type "알림 유형: PRICE, AI_SCORE, NEWS"
        decimal(15_2) price_above "가격 상한 조건"
        decimal(15_2) price_below "가격 하한 조건"
        integer ai_score_above "AI 점수 상한 조건"
        boolean is_active "활성 상태"
        timestamp created_at "생성일시"
    }

    ALERT_HISTORY {
        bigint id PK "이력 ID"
        bigint alert_id FK "알림 ID"
        bigint user_id FK "사용자 ID"
        bigint stock_id FK "종목 ID"
        varchar(10) signal_type "신호: BUY, SELL"
        decimal(15_2) triggered_price "발생 시점 가격"
        integer triggered_ai_score "발생 시점 AI 점수"
        text reason "발생 사유"
        timestamp triggered_at "발생일시"
        boolean is_read "읽음 여부"
    }

    USER_WATCHLIST {
        bigint id PK "관심 ID"
        bigint user_id FK "사용자 ID"
        bigint stock_id FK "종목 ID"
        integer display_order "표시 순서"
        timestamp created_at "등록일시"
    }

    %% 관계 정의
    USERS ||--o{ ALERTS : "설정"
    USERS ||--o{ ALERT_HISTORY : "수신"
    USERS ||--o{ USER_WATCHLIST : "관심종목"

    STOCKS ||--o{ STOCK_PRICES : "가격이력"
    STOCKS ||--|| STOCK_REALTIME : "실시간가격"
    STOCKS ||--o{ AI_ANALYSIS : "AI분석"
    STOCKS ||--o{ NEWS_STOCKS : "관련뉴스"
    STOCKS ||--o{ ALERTS : "알림대상"
    STOCKS ||--o{ ALERT_HISTORY : "알림이력"
    STOCKS ||--o{ USER_WATCHLIST : "관심대상"

    NEWS ||--o{ NEWS_STOCKS : "관련종목"

    ALERTS ||--o{ ALERT_HISTORY : "발생이력"
```

## 테이블 상세 설명

### 1. USERS (사용자)
- 시스템 사용자 정보를 저장
- JWT 인증에 사용되는 계정 정보 포함

### 2. STOCKS (종목)
- 주식 종목 마스터 데이터
- 종목 코드, 이름, 시장 정보 등 기본 정보

### 3. STOCK_PRICES (주가 이력)
- 일별/분별 OHLCV 데이터 저장
- 캔들 차트 렌더링에 사용

### 4. STOCK_REALTIME (실시간 시세)
- 최신 시세 정보 (Redis에도 캐싱)
- WebSocket을 통해 클라이언트에 푸시

### 5. AI_ANALYSIS (AI 분석)
- AI 서비스의 분석 결과 저장
- 추천 점수, 근거, 뉴스 감성 통계

### 6. NEWS (뉴스)
- 수집된 뉴스 기사 및 감성 분석 결과
- AI 서비스에서 분석한 sentiment 저장

### 7. NEWS_STOCKS (뉴스-종목 관계)
- 뉴스와 관련 종목 간의 다대다 관계
- 연관도 점수로 관련성 표현

### 8. ALERTS (알림 설정)
- 사용자별 알림 조건 설정
- 가격, AI 점수 등 다양한 조건 지원

### 9. ALERT_HISTORY (알림 이력)
- 발생한 알림 기록
- 사용자 알림 목록에 표시

### 10. USER_WATCHLIST (관심 종목)
- 사용자별 관심 종목 목록
- 대시보드에 우선 표시

## 인덱스 전략

```sql
-- 자주 조회되는 컬럼에 인덱스 생성
CREATE INDEX idx_stock_prices_stock_date ON stock_prices(stock_id, trade_date DESC);
CREATE INDEX idx_ai_analysis_stock_date ON ai_analysis(stock_id, analyzed_at DESC);
CREATE INDEX idx_news_published ON news(published_at DESC);
CREATE INDEX idx_news_sentiment ON news(sentiment);
CREATE INDEX idx_alerts_user_active ON alerts(user_id, is_active);
CREATE INDEX idx_alert_history_user_read ON alert_history(user_id, is_read, triggered_at DESC);
```
