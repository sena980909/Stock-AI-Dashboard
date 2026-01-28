# Project Name: Stock-AI-Dashboard (Web Platform)

## 1. 프로젝트 개요 (Project Overview)
이 프로젝트는 실시간 주가 데이터와 뉴스 감성 분석 결과를 **웹 대시보드** 형태로 시각화하여 제공하는 투자 보조 플랫폼입니다.
사용자는 웹 브라우저를 통해 실시간 매수 신호 알림을 받고, AI가 분석한 근거(뉴스 요약, 감성 점수)를 차트와 함께 확인할 수 있습니다.
MSA 구조를 지향하며, **WebSocket**을 통해 데이터의 실시간성을 보장합니다.

## 2. 기술 스택 (Tech Stack)

### Backend (Core & API)
- **Language:** Java 17+
- **Framework:** Spring Boot 3.x
- **Network:** REST API, WebSocket (STOMP)
- **Database:** PostgreSQL (이력 데이터), Redis (실시간 시세 캐싱 & Pub/Sub)
- **Messaging:** Apache Kafka (데이터 파이프라인)
- **Security:** Spring Security + JWT

### Frontend (Web)
- **Framework:** React.js (Vite) 또는 Vue.js 3
- **Visualization:** Recharts (주식 차트), ApexCharts
- **Styling:** Tailwind CSS (빠른 UI 구성을 위해 사용)
- **HTTP Client:** Axios

### AI Service
- **Language:** Python FastAPI
- **NLP:** KoBERT 또는 OpenAI API (뉴스 긍정/부정 분류)

## 3. 시스템 아키텍처 및 데이터 흐름
1. **Ingestion:** 수집기가 주식/뉴스 데이터를 수집해 Kafka로 전송.
2. **Processing:**
   - Spring Boot가 Kafka 데이터를 소비(Consume).
   - Redis에 최신 상태 업데이트.
   - **WebSocket Channel**(`/topic/stock-updates`)로 구독 중인 웹 클라이언트에게 실시간 데이터 푸시.
3. **Visualization:**
   - 웹 클라이언트는 초기 로딩 시 REST API로 과거 데이터를 조회.
   - 이후 WebSocket 연결을 통해 실시간으로 차트와 알림 목록을 갱신(Re-rendering).

## 4. 핵심 기능 요구사항 (Web Features)
1. **메인 대시보드:**
   - 실시간 추천 종목 리스트 (AI 점수 순).
   - 실시간 뉴스 피드 (호재/악재 태그 표시).
2. **상세 페이지:**
   - 종목별 캔들 차트 (Candlestick Chart) + 매수 시점 마킹.
   - AI 분석 리포트 ("이 종목은 최근 긍정적 기사가 30% 증가했습니다").
3. **관리자/시스템 뷰 (백엔드 포트폴리오용):**
   - 현재 Kafka 메시지 처리량이나 대기열(Lag) 상태를 보여주는 간단한 상태창 (옵션).

## 5. 클로드 요청 사항 (Instructions)
1. **프로젝트 구조:** Spring Boot (Backend)와 React (Frontend)를 포함하는 전체 디렉토리 구조를 잡아줘.
2. **API 명세:** 프론트엔드와 통신할 REST API 목록(Method, URI, Description)을 표로 정리해줘.
3. **WebSocket 설정:** Spring Boot에서 STOMP 프로토콜을 설정하고, React에서 이를 구독(Subscribe)하는 예시 코드를 작성해줘.
4. **DB 설계:** PostgreSQL ERD 구조를 Mermaid 문법으로 작성해줘.

---

## 6. 개발 로드맵 (Development Phases) - 중요!
우리는 아래 순서대로 개발을 진행할 것입니다. **현재 Phase가 끝나지 않으면 다음 Phase로 넘어가지 마세요.**

### Phase 1: 환경 구축 및 스켈레톤 (Infrastructure)
- Docker Compose로 Kafka, Zookeeper, Postgres, Redis 실행 환경 구축.
- Spring Boot 프로젝트 생성 및 기본 패키지 구조(Domain-driven) 설정.
- DB 연결 및 Health Check API 작성 (`/api/health`).

### Phase 2: 데이터 수집 파이프라인 (Data Ingestion)
- **Kafka Producer 구현:** 주식 API(또는 Mock Data)에서 시세를 조회하여 `stock-price` 토픽으로 발행.
- **Schedule:** 1초마다 데이터를 수집하는 스케줄러(`@Scheduled`) 구현.
- **Log:** 데이터 발행 여부를 콘솔 로그로 확인.

### Phase 3: 데이터 처리 및 AI 연동 (Processing & AI)
- **Kafka Consumer 구현:** `stock-price` 토픽을 구독하여 DB에 저장.
- **AI Service (Python/Mock):** 뉴스 데이터를 분석하여 `sentiment-score`를 반환하는 로직(또는 Mock API).
- **Redis Caching:** 최신 주가와 AI 점수를 Redis에 캐싱하여 조회 속도 최적화.

### Phase 4: 실시간 웹 시각화 (Real-time Visualization)
- **WebSocket (STOMP):** 백엔드에서 프론트엔드로 실시간 데이터 푸시 (`/topic/stock`).
- **React Frontend:** 차트 라이브러리(Recharts)를 연동하여 실시간 주가 그래프 그리기.
- **Dashboard:** AI 추천 점수에 따른 매수 신호 알림 UI 구현.

### Phase 5: 안정화 및 문서화 (Refactoring & Docs)
- **Exception Handling:** Kafka 연결 실패, API 타임아웃 등 예외 상황 처리 강화.
- **Performance:** 불필요한 로그 제거 및 배치 처리(Batch Processing) 적용 검토.
- **Final README:** 포트폴리오 제출용 최종 문서 작성.

---