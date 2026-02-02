import React, { useState, useEffect } from 'react'
import { TopStock } from '../../types'
import { stockApi } from '../../services/api'
import TopRankCard from './TopRankCard'

interface TopRankListProps {
  onStockClick?: (stockCode: string) => void
}

const TopRankList: React.FC<TopRankListProps> = ({ onStockClick }) => {
  const [stocks, setStocks] = useState<TopStock[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)

  const fetchTopStocks = async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await stockApi.getTopRankStocks()
      setStocks(data)
      setLastUpdated(new Date())
    } catch (err) {
      console.error('Failed to fetch top stocks:', err)
      setError('데이터를 불러오는데 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchTopStocks()

    // 10분마다 자동 갱신
    const interval = setInterval(fetchTopStocks, 10 * 60 * 1000)
    return () => clearInterval(interval)
  }, [])

  const handleRefresh = async () => {
    try {
      await stockApi.refreshTopRankCache()
      await fetchTopStocks()
    } catch (err) {
      console.error('Failed to refresh cache:', err)
      setError('캐시 갱신에 실패했습니다.')
    }
  }

  if (loading && stocks.length === 0) {
    return (
      <div className="bg-white rounded-lg shadow-md p-6">
        <div className="animate-pulse space-y-4">
          <div className="h-6 bg-gray-200 rounded w-1/3"></div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
            {[...Array(10)].map((_, i) => (
              <div key={i} className="h-48 bg-gray-200 rounded"></div>
            ))}
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="bg-white rounded-lg shadow-md p-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-xl font-bold text-gray-900">
            시가총액 Top 10
          </h2>
          <p className="text-sm text-gray-500 mt-1">
            대한민국 시가총액 상위 10개 기업의 AI 분석 결과
          </p>
        </div>
        <div className="flex items-center gap-4">
          {lastUpdated && (
            <span className="text-xs text-gray-400">
              마지막 업데이트: {lastUpdated.toLocaleTimeString()}
            </span>
          )}
          <button
            onClick={handleRefresh}
            disabled={loading}
            className="px-3 py-1.5 text-sm bg-blue-500 text-white rounded hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {loading ? '갱신 중...' : '새로고침'}
          </button>
        </div>
      </div>

      {/* Error Message */}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded mb-4">
          {error}
          <button
            onClick={fetchTopStocks}
            className="ml-2 underline hover:no-underline"
          >
            다시 시도
          </button>
        </div>
      )}

      {/* Stock Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
        {stocks.map((stock) => (
          <TopRankCard
            key={stock.stockCode}
            stock={stock}
            onClick={onStockClick}
          />
        ))}
      </div>

      {/* Empty State */}
      {!loading && stocks.length === 0 && !error && (
        <div className="text-center py-12 text-gray-500">
          <p>표시할 데이터가 없습니다.</p>
          <button
            onClick={fetchTopStocks}
            className="mt-2 text-blue-500 hover:underline"
          >
            새로고침
          </button>
        </div>
      )}

      {/* Legend */}
      <div className="mt-6 pt-4 border-t border-gray-100">
        <div className="flex flex-wrap gap-4 text-xs text-gray-500">
          <div className="flex items-center gap-1">
            <span className="w-3 h-3 rounded bg-green-500"></span>
            <span>강력 매수</span>
          </div>
          <div className="flex items-center gap-1">
            <span className="w-3 h-3 rounded bg-green-400"></span>
            <span>매수</span>
          </div>
          <div className="flex items-center gap-1">
            <span className="w-3 h-3 rounded bg-gray-400"></span>
            <span>중립</span>
          </div>
          <div className="flex items-center gap-1">
            <span className="w-3 h-3 rounded bg-red-400"></span>
            <span>매도</span>
          </div>
          <div className="flex items-center gap-1">
            <span className="w-3 h-3 rounded bg-red-500"></span>
            <span>강력 매도</span>
          </div>
        </div>
      </div>
    </div>
  )
}

export default TopRankList
