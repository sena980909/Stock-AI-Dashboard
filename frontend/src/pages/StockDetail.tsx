import { useParams } from 'react-router-dom'
import { useEffect, useState } from 'react'
import CandlestickChart from '../components/chart/CandlestickChart'
import AIReport from '../components/dashboard/AIReport'
import { stockApi } from '../services/api'
import { StockData, AIAnalysis } from '../types'

function StockDetail() {
  const { symbol } = useParams<{ symbol: string }>()
  const [stockData, setStockData] = useState<StockData[]>([])
  const [aiAnalysis, setAIAnalysis] = useState<AIAnalysis | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!symbol) return

    const fetchData = async () => {
      try {
        const [priceData, analysisData] = await Promise.all([
          stockApi.getStockHistory(symbol),
          stockApi.getAIAnalysis(symbol)
        ])
        setStockData(priceData)
        setAIAnalysis(analysisData)
      } catch (error) {
        console.error('Failed to fetch stock data:', error)
      } finally {
        setLoading(false)
      }
    }

    fetchData()
  }, [symbol])

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
      </div>
    )
  }

  return (
    <div className="container mx-auto px-4 py-8">
      <header className="mb-8">
        <h1 className="text-3xl font-bold text-gray-800">{symbol}</h1>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white rounded-lg shadow p-6">
          <h2 className="text-xl font-semibold mb-4">Price Chart</h2>
          <CandlestickChart data={stockData} />
        </div>
        <div>
          <AIReport analysis={aiAnalysis} />
        </div>
      </div>
    </div>
  )
}

export default StockDetail
