import React, { useState, useEffect, useCallback } from 'react'
import { StockSearchResult } from '../../types'
import { stockApi, interestApi, Kospi200Response } from '../../services/api'
import Kospi200Card from './Kospi200Card'

interface Kospi200ListProps {
  onStockClick?: (stockCode: string) => void
}

const Kospi200List: React.FC<Kospi200ListProps> = ({ onStockClick }) => {
  const [stocks, setStocks] = useState<StockSearchResult[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [currentPage, setCurrentPage] = useState(1)
  const [totalPages, setTotalPages] = useState(20)
  const [totalCount, setTotalCount] = useState(200)
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
  const [favorites, setFavorites] = useState<Set<string>>(new Set())

  const ITEMS_PER_PAGE = 10

  // 관심 종목 목록 로드
  const loadFavorites = useCallback(async () => {
    try {
      const interestStocks = await interestApi.getInterestStocks()
      setFavorites(new Set(interestStocks.map(s => s.stockCode)))
    } catch (err) {
      console.error('Failed to load favorites:', err)
    }
  }, [])

  // 코스피 200 데이터 로드
  const fetchStocks = useCallback(async (page: number) => {
    try {
      setLoading(true)
      setError(null)
      const response: Kospi200Response = await stockApi.getKospi200(page, ITEMS_PER_PAGE)
      setStocks(response.data)
      setTotalPages(response.totalPages)
      setTotalCount(response.totalCount)
      setLastUpdated(new Date())
    } catch (err) {
      console.error('Failed to fetch stocks:', err)
      setError('데이터를 불러오는데 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadFavorites()
    fetchStocks(currentPage)
  }, [currentPage, fetchStocks, loadFavorites])

  // 페이지 변경
  const handlePageChange = (page: number) => {
    if (page >= 1 && page <= totalPages) {
      setCurrentPage(page)
      window.scrollTo({ top: 0, behavior: 'smooth' })
    }
  }

  // 새로고침
  const handleRefresh = async () => {
    try {
      await stockApi.refreshKospi200Cache()
      await fetchStocks(currentPage)
    } catch (err) {
      console.error('Failed to refresh:', err)
      setError('새로고침에 실패했습니다.')
    }
  }

  // 즐겨찾기 토글
  const handleFavoriteToggle = async (stockCode: string, stockName: string) => {
    try {
      if (favorites.has(stockCode)) {
        await interestApi.removeInterestStock(stockCode)
        setFavorites(prev => {
          const newSet = new Set(prev)
          newSet.delete(stockCode)
          return newSet
        })
      } else {
        await interestApi.addInterestStock(stockCode, stockName)
        setFavorites(prev => new Set(prev).add(stockCode))
      }
    } catch (err) {
      console.error('Failed to toggle favorite:', err)
    }
  }

  // 페이지네이션 번호 생성
  const getPageNumbers = () => {
    const pages: (number | string)[] = []
    const showPages = 5

    if (totalPages <= showPages + 2) {
      for (let i = 1; i <= totalPages; i++) {
        pages.push(i)
      }
    } else {
      pages.push(1)

      if (currentPage > 3) {
        pages.push('...')
      }

      const start = Math.max(2, currentPage - 1)
      const end = Math.min(totalPages - 1, currentPage + 1)

      for (let i = start; i <= end; i++) {
        if (!pages.includes(i)) {
          pages.push(i)
        }
      }

      if (currentPage < totalPages - 2) {
        pages.push('...')
      }

      if (!pages.includes(totalPages)) {
        pages.push(totalPages)
      }
    }

    return pages
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
            코스피 200
          </h2>
          <p className="text-sm text-gray-500 mt-1">
            코스피 상위 {totalCount}개 종목 실시간 현황 (페이지 {currentPage}/{totalPages})
          </p>
        </div>
        <div className="flex items-center gap-4">
          {lastUpdated && (
            <span className="text-xs text-gray-400">
              {lastUpdated.toLocaleTimeString()}
            </span>
          )}
          <button
            onClick={handleRefresh}
            disabled={loading}
            className="px-3 py-1.5 text-sm bg-blue-500 text-white rounded hover:bg-blue-600 disabled:opacity-50 transition-colors"
          >
            {loading ? '로딩...' : '새로고침'}
          </button>
        </div>
      </div>

      {/* Error */}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded mb-4">
          {error}
          <button onClick={() => fetchStocks(currentPage)} className="ml-2 underline">
            다시 시도
          </button>
        </div>
      )}

      {/* Stock Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
        {stocks.map((stock, index) => (
          <Kospi200Card
            key={stock.code}
            stock={stock}
            rank={(currentPage - 1) * ITEMS_PER_PAGE + index + 1}
            isFavorite={favorites.has(stock.code)}
            onFavoriteToggle={handleFavoriteToggle}
            onClick={onStockClick}
          />
        ))}
      </div>

      {/* Pagination */}
      <div className="mt-8 flex items-center justify-center gap-2">
        <button
          onClick={() => handlePageChange(currentPage - 1)}
          disabled={currentPage === 1}
          className="px-3 py-2 text-sm border rounded hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          이전
        </button>

        {getPageNumbers().map((page, index) => (
          <button
            key={index}
            onClick={() => typeof page === 'number' && handlePageChange(page)}
            disabled={page === '...'}
            className={`px-3 py-2 text-sm border rounded transition-colors ${
              page === currentPage
                ? 'bg-blue-500 text-white border-blue-500'
                : page === '...'
                ? 'cursor-default'
                : 'hover:bg-gray-50'
            }`}
          >
            {page}
          </button>
        ))}

        <button
          onClick={() => handlePageChange(currentPage + 1)}
          disabled={currentPage === totalPages}
          className="px-3 py-2 text-sm border rounded hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          다음
        </button>
      </div>

      {/* Legend */}
      <div className="mt-6 pt-4 border-t border-gray-100">
        <div className="flex flex-wrap gap-4 text-xs text-gray-500">
          <div className="flex items-center gap-1">
            <span className="w-3 h-3 rounded bg-green-500"></span>
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
            <svg className="w-4 h-4 text-yellow-400 fill-current" viewBox="0 0 20 20">
              <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
            </svg>
            <span>관심종목</span>
          </div>
        </div>
      </div>
    </div>
  )
}

export default Kospi200List
