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
  const [showPageSelector, setShowPageSelector] = useState(false)
  const [pageSelectorPosition, setPageSelectorPosition] = useState<'left' | 'right'>('left')

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
    if (page >= 1 && page <= totalPages && page !== currentPage) {
      setLoading(true)
      setStocks([]) // 기존 데이터 클리어하여 덮어쓰기 방지
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

  // 페이지네이션 번호 생성 (1 2 3 ... 18 19 20 형식)
  const getPageNumbers = (): { page: number | string; position?: 'left' | 'right' }[] => {
    const pages: { page: number | string; position?: 'left' | 'right' }[] = []

    if (totalPages <= 10) {
      // 10페이지 이하면 모두 표시
      for (let i = 1; i <= totalPages; i++) {
        pages.push({ page: i })
      }
    } else {
      // 항상 앞쪽 3개 표시
      pages.push({ page: 1 }, { page: 2 }, { page: 3 })

      // 현재 페이지가 앞쪽에 있으면
      if (currentPage <= 4) {
        pages.push({ page: 4 }, { page: 5 }, { page: '...', position: 'right' })
      }
      // 현재 페이지가 중간에 있으면
      else if (currentPage >= 5 && currentPage <= totalPages - 4) {
        pages.push({ page: '...', position: 'left' })
        pages.push({ page: currentPage - 1 }, { page: currentPage }, { page: currentPage + 1 })
        pages.push({ page: '...', position: 'right' })
      }
      // 현재 페이지가 뒤쪽에 있으면
      else {
        pages.push({ page: '...', position: 'left' }, { page: totalPages - 4 }, { page: totalPages - 3 })
      }

      // 항상 뒤쪽 3개 표시
      const existingPages = pages.map(p => p.page)
      if (!existingPages.includes(totalPages - 2)) pages.push({ page: totalPages - 2 })
      if (!existingPages.includes(totalPages - 1)) pages.push({ page: totalPages - 1 })
      if (!existingPages.includes(totalPages)) pages.push({ page: totalPages })
    }

    // 중복 제거
    const uniquePages: { page: number | string; position?: 'left' | 'right' }[] = []
    let prevWasEllipsis = false
    for (const p of pages) {
      if (p.page === '...') {
        if (!prevWasEllipsis) {
          uniquePages.push(p)
          prevWasEllipsis = true
        }
      } else {
        const exists = uniquePages.find(up => up.page === p.page)
        if (!exists) {
          uniquePages.push(p)
        }
        prevWasEllipsis = false
      }
    }

    return uniquePages
  }

  // 중간 페이지 목록 가져오기 (... 클릭 시 표시)
  const getMiddlePages = () => {
    if (totalPages <= 10) return []
    // 4~(totalPages-3) 범위의 페이지
    const middlePages: number[] = []
    for (let i = 4; i <= totalPages - 3; i++) {
      middlePages.push(i)
    }
    return middlePages
  }

  // ... 버튼 클릭 핸들러
  const handleEllipsisClick = (position: 'left' | 'right') => {
    setPageSelectorPosition(position)
    setShowPageSelector(true)
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
      <div className="relative min-h-[400px]">
        {/* 로딩 오버레이 */}
        {loading && (
          <div className="absolute inset-0 bg-white bg-opacity-80 z-10 flex items-center justify-center rounded-lg">
            <div className="text-center">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mx-auto mb-3"></div>
              <p className="text-gray-600 font-medium">페이지 {currentPage} 로딩 중...</p>
            </div>
          </div>
        )}

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
      </div>

      {/* Pagination */}
      <div className="mt-8 flex items-center justify-center gap-2 relative">
        <button
          onClick={() => handlePageChange(currentPage - 1)}
          disabled={currentPage === 1}
          className="px-3 py-2 text-sm border rounded hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          이전
        </button>

        {getPageNumbers().map((item, index) => (
          <div key={index} className="relative">
            <button
              onClick={() => {
                if (typeof item.page === 'number') {
                  handlePageChange(item.page)
                } else if (item.page === '...' && item.position) {
                  handleEllipsisClick(item.position)
                }
              }}
              className={`px-3 py-2 text-sm border rounded transition-colors ${
                item.page === currentPage
                  ? 'bg-blue-500 text-white border-blue-500'
                  : item.page === '...'
                  ? 'hover:bg-blue-50 hover:border-blue-300 cursor-pointer'
                  : 'hover:bg-gray-50'
              }`}
            >
              {item.page}
            </button>
          </div>
        ))}

        <button
          onClick={() => handlePageChange(currentPage + 1)}
          disabled={currentPage === totalPages}
          className="px-3 py-2 text-sm border rounded hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          다음
        </button>

        {/* 페이지 선택 팝업 */}
        {showPageSelector && (
          <>
            {/* 배경 오버레이 */}
            <div
              className="fixed inset-0 z-40"
              onClick={() => setShowPageSelector(false)}
            />
            {/* 팝업 */}
            <div className={`absolute bottom-full mb-2 z-50 bg-white rounded-lg shadow-xl border p-3 ${
              pageSelectorPosition === 'left' ? 'left-1/4' : 'right-1/4'
            }`}>
              <div className="text-xs text-gray-500 mb-2 font-medium">페이지 선택</div>
              <div className="grid grid-cols-5 gap-1 max-w-xs">
                {getMiddlePages().map((page) => (
                  <button
                    key={page}
                    onClick={() => {
                      handlePageChange(page)
                      setShowPageSelector(false)
                    }}
                    className={`px-2 py-1.5 text-sm rounded transition-colors ${
                      page === currentPage
                        ? 'bg-blue-500 text-white'
                        : 'bg-gray-100 hover:bg-blue-100 hover:text-blue-600'
                    }`}
                  >
                    {page}
                  </button>
                ))}
              </div>
            </div>
          </>
        )}
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
