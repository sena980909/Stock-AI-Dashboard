export interface Stock {
  symbol: string
  name: string
  price: number
  change: number
  changePercent: number
  aiScore: number
  sentiment: 'positive' | 'negative' | 'neutral'
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
  sentiment: 'positive' | 'negative' | 'neutral'
  sentimentScore: number
  source: string
  publishedAt: string
  relatedStocks: string[]
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
