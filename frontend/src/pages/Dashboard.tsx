import { useEffect, useState } from 'react'
import StockList from '../components/dashboard/StockList'
import NewsFeed from '../components/dashboard/NewsFeed'
import { useStockWebSocket } from '../hooks/useStockWebSocket'
import { Stock } from '../types'
import { stockApi } from '../services/api'

function Dashboard() {
  const { stocks: wsStocks, isConnected } = useStockWebSocket()
  const [apiStocks, setApiStocks] = useState<Stock[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const fetchStocks = async () => {
      try {
        const data = await stockApi.getRecommendedStocks()
        setApiStocks(data)
      } catch (err) {
        console.error('Failed to fetch stocks:', err)
      } finally {
        setLoading(false)
      }
    }
    fetchStocks()
    // 30초마다 갱신
    const interval = setInterval(fetchStocks, 30000)
    return () => clearInterval(interval)
  }, [])

  const displayStocks = wsStocks.length > 0 ? wsStocks : apiStocks

  return (
    <div className="container mx-auto px-4 py-8">
      <header className="mb-8">
        <h1 className="text-3xl font-bold text-gray-800">Stock AI Dashboard</h1>
        <div className="flex items-center mt-2">
          <span className={`w-3 h-3 rounded-full mr-2 ${isConnected ? 'bg-green-500' : 'bg-red-500'}`}></span>
          <span className="text-sm text-gray-600">
            {isConnected ? '실시간 연결됨' : '연결 끊김'}
          </span>
        </div>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2">
          {loading ? (
            <div className="bg-white rounded-lg shadow p-8 text-center text-gray-500">
              주가 데이터 로딩 중...
            </div>
          ) : (
            <StockList stocks={displayStocks} />
          )}
        </div>
        <div>
          <NewsFeed />
        </div>
      </div>
    </div>
  )
}

export default Dashboard
