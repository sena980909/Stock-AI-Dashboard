export interface Stock {
  symbol: string
  name: string
  price: number
  change: number
  changePercent: number
  aiScore: number
  sentiment: 'positive' | 'negative' | 'neutral'
  recommendReason?: string  // AI 추천 이유 (한줄 요약)
}

export interface StockData {
  timestamp: string
  open: number
  high: number
  low: number
  close: number
  volume: number
}

export interface News {
  id: string
  title: string
  summary: string
  content?: string  // 뉴스 본문
  sentiment: 'positive' | 'negative' | 'neutral'
  sentimentScore: number
  source: string
  publishedAt: string
  relatedStocks: string[]
  keywords?: string[]  // 관련 키워드
  url?: string  // 원문 URL
}

export interface AIAnalysis {
  symbol: string
  score: number
  recommendation: 'buy' | 'sell' | 'hold'
  reasons: string[]
  newsAnalysis: {
    positive: number
    negative: number
    neutral: number
  }
  lastUpdated: string
}

export interface WebSocketMessage {
  type: 'STOCK_UPDATE' | 'NEWS_UPDATE' | 'SIGNAL_ALERT'
  data: Stock | News | SignalAlert
}

export interface SignalAlert {
  symbol: string
  signal: 'buy' | 'sell'
  price: number
  aiScore: number
  reason: string
  timestamp: string
}

// Top 10 종목 DTO
export interface TopStock {
  rank: number
  stockCode: string
  stockName: string
  currentPrice: number
  changePercent: number
  marketCap: number
  aiScore: number
  summary: string
  signalType: 'STRONG_BUY' | 'BUY' | 'NEUTRAL' | 'SELL' | 'STRONG_SELL'
  updatedAt: string
}

// SWOT 분석
export interface SwotAnalysis {
  strengths: string[]
  weaknesses: string[]
  opportunities: string[]
  threats: string[]
}

// 기술적 지표
export interface TechnicalIndicators {
  trend: 'UPTREND' | 'DOWNTREND' | 'SIDEWAYS'
  rsiValue: number
  rsiSignal: 'OVERBOUGHT' | 'OVERSOLD' | 'NEUTRAL'
  macdValue: number
  macdSignal: 'BUY' | 'SELL' | 'NEUTRAL'
  movingAverage: 'ABOVE_MA' | 'BELOW_MA'
  volumeChange: number
}

// 감성 분석 데이터
export interface SentimentData {
  positiveNewsCount: number
  negativeNewsCount: number
  neutralNewsCount: number
  overallSentiment: number
  recentHeadlines: string[]
}

// 검색 결과
export interface StockSearchResult {
  code: string
  name: string
  market: string
  currentPrice: number | null
  changeRate: number | null
  marketCap: number | null
  aiScore: number | null
  signalType: string | null
  formattedMarketCap?: string
  formattedPrice?: string
}

// AI 상세 리포트
export interface AiReport {
  stockCode: string
  stockName: string
  sector: string              // 업종
  companyDescription: string  // 회사 설명
  currentPrice: number
  changePercent: number
  marketCap: number
  totalScore: number
  technicalScore: number
  sentimentScore: number
  signalType: 'STRONG_BUY' | 'BUY' | 'NEUTRAL' | 'SELL' | 'STRONG_SELL'
  summary: string
  swot: SwotAnalysis
  technicalIndicators: TechnicalIndicators
  sentimentData: SentimentData
  analyzedAt: string
}
