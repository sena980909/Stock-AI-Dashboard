import axios from 'axios'
import { Stock, StockData, News, AIAnalysis, TopStock, AiReport, StockSearchResult } from '../types'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// Request interceptor for auth token
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Response interceptor for error handling
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export const stockApi = {
  getRecommendedStocks: async (): Promise<Stock[]> => {
    const response = await api.get('/stocks/recommended')
    return response.data.stocks ?? response.data
  },

  getStockHistory: async (symbol: string, days: number = 60): Promise<StockData[]> => {
    const response = await api.get(`/stocks/${symbol}/history?days=${days}`)
    // 백엔드 응답: { stockCode, days, count, data: [...] }
    const data = response.data.data ?? response.data
    return Array.isArray(data) ? data : []
  },

  getAIAnalysis: async (symbol: string): Promise<AIAnalysis> => {
    const response = await api.get(`/stocks/${symbol}/analysis`)
    return response.data
  },

  getCurrentPrice: async (symbol: string): Promise<Stock> => {
    const response = await api.get(`/stocks/${symbol}/current`)
    return response.data
  },

  // 시가총액 Top 10 종목 조회
  getTopRankStocks: async (): Promise<TopStock[]> => {
    const response = await api.get('/stocks/top-rank')
    return response.data.data ?? []
  },

  // AI 상세 리포트 조회
  getAiReport: async (stockCode: string): Promise<AiReport> => {
    const response = await api.get(`/stocks/${stockCode}/ai-report`)
    return response.data.data
  },

  // Top 10 캐시 갱신
  refreshTopRankCache: async (): Promise<void> => {
    await api.post('/stocks/top-rank/refresh')
  },

  // 종목 검색
  searchStocks: async (keyword: string): Promise<StockSearchResult[]> => {
    const response = await api.get(`/stocks/search?keyword=${encodeURIComponent(keyword)}`)
    return response.data.data ?? []
  },

  // 종목 코드로 직접 조회
  getStockByCode: async (code: string): Promise<StockSearchResult | null> => {
    try {
      const response = await api.get(`/stocks/code/${code}`)
      return response.data.data
    } catch {
      return null
    }
  },

  // 인기 검색어 조회
  getPopularSearchTerms: async (): Promise<string[]> => {
    const response = await api.get('/stocks/search/popular')
    return response.data.data ?? []
  }
}

export const newsApi = {
  getLatestNews: async (): Promise<News[]> => {
    const response = await api.get('/news/latest')
    return response.data
  },

  getNewsByStock: async (symbol: string): Promise<News[]> => {
    const response = await api.get(`/news/stock/${symbol}`)
    return response.data
  }
}

export const authApi = {
  login: async (email: string, password: string): Promise<{ token: string }> => {
    const response = await api.post('/auth/login', { email, password })
    return response.data
  },

  register: async (email: string, password: string, name: string): Promise<void> => {
    await api.post('/auth/register', { email, password, name })
  }
}

export const healthApi = {
  check: async (): Promise<{ status: string }> => {
    const response = await api.get('/health')
    return response.data
  }
}

export default api
