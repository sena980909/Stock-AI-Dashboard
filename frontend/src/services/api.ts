import axios from 'axios'
import { Stock, StockData, News, AIAnalysis } from '../types'

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

  getStockHistory: async (symbol: string): Promise<StockData[]> => {
    const response = await api.get(`/stocks/${symbol}/history`)
    return response.data
  },

  getAIAnalysis: async (symbol: string): Promise<AIAnalysis> => {
    const response = await api.get(`/stocks/${symbol}/analysis`)
    return response.data
  },

  getCurrentPrice: async (symbol: string): Promise<Stock> => {
    const response = await api.get(`/stocks/${symbol}/current`)
    return response.data
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
