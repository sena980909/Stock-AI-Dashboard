import { useEffect, useState } from 'react'
import { interestApi } from '../../services/api'

interface InterestStock {
  id: number
  stockCode: string
  stockName: string
  currentPrice?: number
  changePercent?: number
}

interface MyWatchlistBarProps {
  onStockClick?: (stockCode: string) => void
}

function MyWatchlistBar({ onStockClick }: MyWatchlistBarProps) {
  const [watchlist, setWatchlist] = useState<InterestStock[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchWatchlist()
    // 30초마다 갱신
    const interval = setInterval(fetchWatchlist, 30000)
    return () => clearInterval(interval)
  }, [])

  const fetchWatchlist = async () => {
    try {
      const data = await interestApi.getInterestStocks()
      setWatchlist(data)
    } catch (err) {
      console.error('Failed to fetch watchlist:', err)
    } finally {
      setLoading(false)
    }
  }

  if (loading) {
    return (
      <div className="bg-gradient-to-r from-blue-600 to-blue-700 rounded-lg shadow-md p-4 mb-6">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2 text-white">
            <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
              <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
            </svg>
            <span className="font-bold">내 관심종목</span>
          </div>
          <div className="animate-pulse flex gap-3">
            {[1, 2, 3].map(i => (
              <div key={i} className="w-32 h-16 bg-white/20 rounded-lg"></div>
            ))}
          </div>
        </div>
      </div>
    )
  }

  if (watchlist.length === 0) {
    return (
      <div className="bg-gradient-to-r from-gray-100 to-gray-200 rounded-lg shadow-md p-4 mb-6">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2 text-gray-600">
            <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
              <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
            </svg>
            <span className="font-bold">내 관심종목</span>
          </div>
          <p className="text-gray-500 text-sm">
            관심종목이 없습니다. 코스피 200에서 ⭐ 버튼을 눌러 추가하세요.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="bg-gradient-to-r from-blue-600 to-blue-700 rounded-lg shadow-md p-4 mb-6">
      <div className="flex items-center gap-4">
        {/* 타이틀 */}
        <div className="flex items-center gap-2 text-white shrink-0">
          <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
            <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
          </svg>
          <span className="font-bold">내 관심종목</span>
          <span className="bg-white/20 text-xs px-2 py-0.5 rounded-full">{watchlist.length}</span>
        </div>

        {/* 가로 스크롤 종목 목록 */}
        <div className="flex-1 overflow-x-auto scrollbar-hide">
          <div className="flex gap-3">
            {watchlist.map(stock => (
              <button
                key={stock.id}
                onClick={() => onStockClick?.(stock.stockCode)}
                className="bg-white rounded-lg px-4 py-2 min-w-[140px] hover:bg-blue-50 transition-colors cursor-pointer shadow-sm"
              >
                <div className="flex items-center justify-between gap-2">
                  <div className="text-left">
                    <div className="font-bold text-gray-800 text-sm truncate max-w-[80px]">
                      {stock.stockName}
                    </div>
                    <div className="text-xs text-gray-500">{stock.stockCode}</div>
                  </div>
                  <div className="text-right">
                    {stock.currentPrice ? (
                      <>
                        <div className="text-sm font-mono font-bold text-gray-800">
                          {stock.currentPrice.toLocaleString()}
                        </div>
                        <div className={`text-xs font-bold ${
                          (stock.changePercent ?? 0) >= 0 ? 'text-red-500' : 'text-blue-500'
                        }`}>
                          {(stock.changePercent ?? 0) >= 0 ? '+' : ''}
                          {stock.changePercent?.toFixed(2)}%
                        </div>
                      </>
                    ) : (
                      <div className="text-xs text-gray-400">로딩중...</div>
                    )}
                  </div>
                </div>
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

export default MyWatchlistBar
