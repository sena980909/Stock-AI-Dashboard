# Stock AI Dashboard - REST API 명세서

## Base URL
```
http://localhost:8080/api
```

## 인증 (Authentication)

모든 API 요청은 JWT 토큰을 헤더에 포함해야 합니다 (인증 API 제외).

```
Authorization: Bearer <token>
```

---

## API 목록

### 1. Health Check

| Method | URI | Description |
|--------|-----|-------------|
| GET | `/health` | 서버 상태 확인 |

**Response:**
```json
{
  "status": "UP",
  "timestamp": "2024-01-01T00:00:00Z",
  "services": {
    "database": "UP",
    "redis": "UP",
    "kafka": "UP"
  }
}
```

---

### 2. 인증 API (Auth)

| Method | URI | Description |
|--------|-----|-------------|
| POST | `/auth/register` | 회원가입 |
| POST | `/auth/login` | 로그인 |
| POST | `/auth/refresh` | 토큰 갱신 |
| POST | `/auth/logout` | 로그아웃 |

#### POST `/auth/register`
**Request:**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "name": "홍길동"
}
```

**Response:** `201 Created`
```json
{
  "message": "회원가입이 완료되었습니다."
}
```

#### POST `/auth/login`
**Request:**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "expiresIn": 86400,
  "user": {
    "id": 1,
    "email": "user@example.com",
    "name": "홍길동"
  }
}
```

---

### 3. 주식 API (Stocks)

| Method | URI | Description |
|--------|-----|-------------|
| GET | `/stocks/recommended` | AI 추천 종목 리스트 조회 |
| GET | `/stocks/{symbol}/current` | 특정 종목 현재가 조회 |
| GET | `/stocks/{symbol}/history` | 특정 종목 과거 데이터 조회 |
| GET | `/stocks/{symbol}/analysis` | 특정 종목 AI 분석 결과 조회 |
| GET | `/stocks/search` | 종목 검색 |

#### GET `/stocks/recommended`
**Query Parameters:**
- `limit` (optional): 조회할 종목 수 (default: 10)
- `sortBy` (optional): 정렬 기준 - `aiScore`, `change`, `volume` (default: aiScore)

**Response:**
```json
{
  "stocks": [
    {
      "symbol": "005930",
      "name": "삼성전자",
      "price": 71000,
      "change": 500,
      "changePercent": 0.71,
      "aiScore": 85,
      "sentiment": "positive"
    }
  ],
  "totalCount": 100,
  "lastUpdated": "2024-01-01T09:00:00Z"
}
```

#### GET `/stocks/{symbol}/history`
**Query Parameters:**
- `period` (optional): 조회 기간 - `1D`, `1W`, `1M`, `3M`, `1Y` (default: 1M)
- `interval` (optional): 데이터 간격 - `1m`, `5m`, `1h`, `1d` (default: 1d)

**Response:**
```json
{
  "symbol": "005930",
  "data": [
    {
      "timestamp": "2024-01-01T09:00:00Z",
      "open": 70500,
      "high": 71200,
      "low": 70300,
      "close": 71000,
      "volume": 12500000
    }
  ]
}
```

#### GET `/stocks/{symbol}/analysis`
**Response:**
```json
{
  "symbol": "005930",
  "score": 85,
  "recommendation": "buy",
  "reasons": [
    "최근 긍정적 기사가 30% 증가했습니다",
    "기술적 지표상 상승 추세입니다",
    "외국인 순매수 지속 중입니다"
  ],
  "newsAnalysis": {
    "positive": 45,
    "negative": 20,
    "neutral": 35
  },
  "lastUpdated": "2024-01-01T09:00:00Z"
}
```

---

### 4. 뉴스 API (News)

| Method | URI | Description |
|--------|-----|-------------|
| GET | `/news/latest` | 최신 뉴스 목록 조회 |
| GET | `/news/stock/{symbol}` | 특정 종목 관련 뉴스 조회 |
| GET | `/news/{id}` | 뉴스 상세 조회 |

#### GET `/news/latest`
**Query Parameters:**
- `limit` (optional): 조회할 뉴스 수 (default: 20)
- `sentiment` (optional): 감성 필터 - `positive`, `negative`, `neutral`

**Response:**
```json
{
  "news": [
    {
      "id": "news-001",
      "title": "삼성전자, AI 반도체 수요 급증으로 실적 호조 전망",
      "summary": "삼성전자가 AI 반도체 수요 증가로...",
      "sentiment": "positive",
      "sentimentScore": 0.85,
      "source": "한국경제",
      "publishedAt": "2024-01-01T08:30:00Z",
      "relatedStocks": ["005930", "000660"]
    }
  ],
  "totalCount": 500
}
```

---

### 5. 알림 API (Alerts)

| Method | URI | Description |
|--------|-----|-------------|
| GET | `/alerts` | 사용자 알림 목록 조회 |
| POST | `/alerts/subscribe` | 종목 알림 구독 |
| DELETE | `/alerts/{symbol}` | 종목 알림 구독 해제 |

#### POST `/alerts/subscribe`
**Request:**
```json
{
  "symbol": "005930",
  "conditions": {
    "priceAbove": 75000,
    "priceBelow": 65000,
    "aiScoreAbove": 80
  }
}
```

**Response:** `201 Created`
```json
{
  "id": "alert-001",
  "symbol": "005930",
  "createdAt": "2024-01-01T09:00:00Z"
}
```

---

### 6. 시스템 모니터링 API (Admin)

| Method | URI | Description |
|--------|-----|-------------|
| GET | `/admin/kafka/status` | Kafka 상태 조회 |
| GET | `/admin/metrics` | 시스템 메트릭 조회 |

#### GET `/admin/kafka/status`
**Response:**
```json
{
  "topics": [
    {
      "name": "stock-price",
      "partitions": 3,
      "lag": 0
    },
    {
      "name": "news-sentiment",
      "partitions": 3,
      "lag": 5
    }
  ],
  "consumerGroups": [
    {
      "id": "stock-ai-group",
      "state": "STABLE",
      "members": 2
    }
  ]
}
```

---

## 에러 응답 형식

모든 에러는 다음 형식으로 반환됩니다:

```json
{
  "error": {
    "code": "STOCK_NOT_FOUND",
    "message": "요청한 종목을 찾을 수 없습니다.",
    "timestamp": "2024-01-01T09:00:00Z"
  }
}
```

### 에러 코드

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `UNAUTHORIZED` | 401 | 인증되지 않은 요청 |
| `FORBIDDEN` | 403 | 권한 없음 |
| `STOCK_NOT_FOUND` | 404 | 종목을 찾을 수 없음 |
| `NEWS_NOT_FOUND` | 404 | 뉴스를 찾을 수 없음 |
| `INVALID_PARAMETER` | 400 | 잘못된 파라미터 |
| `RATE_LIMIT_EXCEEDED` | 429 | 요청 한도 초과 |
| `INTERNAL_ERROR` | 500 | 서버 내부 오류 |
