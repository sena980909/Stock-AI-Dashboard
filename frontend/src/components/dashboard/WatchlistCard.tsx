import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import axios from 'axios'

interface InterestStock {
  id: number
  stockCode: string
  stockName: string
  currentPrice?: number
  change?: number
  changePercent?: number
  aiScore?: number
  sentiment?: string
}

function WatchlistCard() {
  const [watchlist, setWatchlist] = useState<InterestStock[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchWatchlist()
  }, [])

  const fetchWatchlist = async () => {
    try {
      const response = await axios.get('/api/interests')
      setWatchlist(response.data)
    } catch (err) {
      console.error('Failed to fetch watchlist:', err)
    } finally {
      setLoading(false)
    }
  }

  const removeFromWatchlist = async (stockCode: string) => {
    try {
      await axios.delete(`/api/interests/${stockCode}`)
      setWatchlist(prev => prev.filter(s => s.stockCode !== stockCode))
    } catch (err) {
      console.error('Failed to remove from watchlist:', err)
    }
  }

  if (loading) {
    return (
      <div className="bg-white rounded-lg shadow p-6">
        <div className="animate-pulse space-y-3">
          {[1, 2, 3].map(i => (
            <div key={i} className="h-12 bg-gray-200 rounded"></div>
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className="bg-white rounded-lg shadow">
      <div className="px-6 py-4 border-b flex justify-between items-center">
        <h3 className="text-lg font-semibold">관심 종목</h3>
        <span className="text-sm text-gray-500">{watchlist.length}개</span>
      </div>

      {watchlist.length === 0 ? (
        <div className="p-6 text-center text-gray-500 text-sm">
          관심 종목이 없습니다.<br/>
          종목을 클릭하여 추가하세요.
        </div>
      ) : (
        <div className="divide-y max-h-[300px] overflow-y-auto">
          {watchlist.map(stock => (
            <div key={stock.id} className="px-4 py-3 flex items-center justify-between hover:bg-gray-50">
              <Link to={`/stock/${stock.stockCode}`} className="flex-1">
                <div className="font-medium text-sm">{stock.stockName}</div>
                <div className="text-xs text-gray-500">{stock.stockCode}</div>
              </Link>

              {stock.currentPrice && (
                <div className="text-right mr-3">
                  <div className="text-sm font-mono">{stock.currentPrice.toLocaleString()}</div>
                  <div className={`text-xs ${(stock.changePercent ?? 0) >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                    {(stock.changePercent ?? 0) >= 0 ? '+' : ''}{stock.changePercent?.toFixed(2)}%
                  </div>
                </div>
              )}

              <button
                onClick={(e) => {
                  e.preventDefault()
                  removeFromWatchlist(stock.stockCode)
                }}
                className="text-gray-400 hover:text-red-500 p-1"
                title="관심 종목에서 제거"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default WatchlistCard
