# Stock-AI-Dashboard

실시간 주가 데이터와 AI 기반 뉴스 감성 분석 결과를 웹 대시보드로 시각화하는 투자 보조 플랫폼입니다.

![Dashboard Preview](https://img.shields.io/badge/Status-Active-brightgreen)
![Java](https://img.shields.io/badge/Java-17+-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green)
![React](https://img.shields.io/badge/React-18-blue)

## 주요 기능

- **실시간 주가 조회**: 네이버 금융 API 연동으로 실시간 주가 표시
- **AI 추천 점수**: 등락률 기반 AI 점수 및 매수/매도 신호
- **실시간 뉴스 피드**: 호재/악재 태그가 포함된 뉴스 목록
- **WebSocket 실시간 연결**: STOMP 프로토콜로 실시간 데이터 푸시
- **종목 상세 페이지**: 캔들 차트 및 AI 분석 리포트

## 기술 스택

### Backend
| 기술 | 버전 | 용도 |
|------|------|------|
| Java | 17+ | 언어 |
| Spring Boot | 3.x | 프레임워크 |
| Spring WebSocket | - | 실시간 통신 (STOMP) |
| Spring Data JPA | - | ORM |
| PostgreSQL | 15 | 이력 데이터 저장 |
| Redis | 7 | 실시간 시세 캐싱 |
| Apache Kafka | 3.x | 메시지 큐 |

### Frontend
| 기술 | 버전 | 용도 |
|------|------|------|
| React | 18 | UI 프레임워크 |
| Vite | 5 | 빌드 도구 |
| TypeScript | 5 | 타입 안정성 |
| Tailwind CSS | 3 | 스타일링 |
| ApexCharts | 4 | 캔들 차트 |
| STOMP.js | 7 | WebSocket 클라이언트 |

## 프로젝트 구조

```
Stock-AI-Dashboard/
├── backend/                    # Spring Boot 백엔드
│   └── src/main/java/com/stockai/dashboard/
│       ├── config/             # WebSocket, Redis, Kafka 설정
│       ├── controller/         # REST API 컨트롤러
│       ├── service/            # 비즈니스 로직
│       ├── repository/         # JPA Repository
│       ├── domain/
│       │   ├── entity/         # JPA Entity
│       │   └── dto/            # 데이터 전송 객체
│       └── websocket/          # WebSocket 핸들러
├── frontend/                   # React 프론트엔드
│   └── src/
│       ├── components/         # UI 컴포넌트
│       ├── pages/              # 페이지 컴포넌트
│       ├── hooks/              # 커스텀 훅 (WebSocket)
│       ├── services/           # API 서비스
│       └── types/              # TypeScript 타입
├── docs/
│   ├── API_SPECIFICATION.md    # REST API 명세
│   └── DATABASE_ERD.md         # ERD (Mermaid)
└── docker-compose.yml          # 인프라 (Kafka, Redis, PostgreSQL)
```

## 빠른 시작

### 1. 사전 요구사항
- Java 17+
- Node.js 18+
- Docker Desktop

### 2. 인프라 실행
```bash
docker-compose up -d
```

### 3. 백엔드 실행
```bash
cd backend
./gradlew bootRun
```

### 4. 프론트엔드 실행
```bash
cd frontend
npm install
npm run dev
```

### 5. 접속
- **Frontend**: http://localhost:3000
- **Backend API**: http://localhost:8080/api
- **Health Check**: http://localhost:8080/api/health

## API 엔드포인트

| Method | URI | 설명 |
|--------|-----|------|
| GET | `/api/health` | 서버 상태 확인 |
| GET | `/api/stocks/recommended` | AI 추천 종목 리스트 |
| GET | `/api/stocks/{symbol}/current` | 특정 종목 현재가 |
| GET | `/api/stocks/{symbol}/history` | 종목 과거 데이터 |
| GET | `/api/stocks/{symbol}/analysis` | AI 분석 결과 |

## WebSocket 엔드포인트

| 엔드포인트 | 설명 |
|-----------|------|
| `ws://localhost:8080/ws` | WebSocket 연결 |
| `/topic/stock-updates` | 실시간 주가 업데이트 구독 |
| `/topic/signals` | 매수/매도 신호 알림 구독 |

## 시스템 아키텍처

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Naver     │────▶│  Spring     │────▶│   React     │
│  Finance    │     │  Boot API   │     │  Frontend   │
│   API       │     │             │     │             │
└─────────────┘     └──────┬──────┘     └─────────────┘
                          │                    ▲
                          │ WebSocket          │
                          └────────────────────┘

┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Kafka     │◀───▶│   Redis     │◀───▶│ PostgreSQL  │
│  (Queue)    │     │  (Cache)    │     │    (DB)     │
└─────────────┘     └─────────────┘     └─────────────┘
```

## 개발 로드맵

- [x] **Phase 1**: 환경 구축 및 스켈레톤
- [x] **Phase 2**: 데이터 수집 파이프라인 (네이버 금융 API)
- [ ] **Phase 3**: Kafka 연동 및 Redis 캐싱
- [x] **Phase 4**: 실시간 웹 시각화 (WebSocket)
- [ ] **Phase 5**: 안정화 및 문서화

## 라이선스

MIT License

## 기여

이슈 및 PR 환영합니다.
