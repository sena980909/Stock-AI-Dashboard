import React, { useState, useEffect, useRef, useCallback } from 'react'
import { StockSearchResult } from '../../types'
import { stockApi } from '../../services/api'

interface StockSearchProps {
  onSelect: (stock: StockSearchResult) => void
  placeholder?: string
}

const StockSearch: React.FC<StockSearchProps> = ({
  onSelect,
  placeholder = '종목명 또는 종목코드 검색...'
}) => {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<StockSearchResult[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [isOpen, setIsOpen] = useState(false)
  const [selectedIndex, setSelectedIndex] = useState(-1)
  const [popularTerms, setPopularTerms] = useState<string[]>([])

  const inputRef = useRef<HTMLInputElement>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // 인기 검색어 로드
  useEffect(() => {
    const loadPopularTerms = async () => {
      try {
        const terms = await stockApi.getPopularSearchTerms()
        setPopularTerms(terms)
      } catch (err) {
        console.error('Failed to load popular terms:', err)
      }
    }
    loadPopularTerms()
  }, [])

  // 검색 함수 (Debounce 적용)
  const performSearch = useCallback(async (searchQuery: string) => {
    if (!searchQuery.trim()) {
      setResults([])
      return
    }

    setIsLoading(true)
    try {
      const searchResults = await stockApi.searchStocks(searchQuery)
      setResults(searchResults)
      setSelectedIndex(-1)
    } catch (err) {
      console.error('Search failed:', err)
      setResults([])
    } finally {
      setIsLoading(false)
    }
  }, [])

  // 입력 변경 핸들러 (Debounce)
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value
    setQuery(value)
    setIsOpen(true)

    // 기존 타이머 클리어
    if (debounceRef.current) {
      clearTimeout(debounceRef.current)
    }

    // 300ms 디바운스
    debounceRef.current = setTimeout(() => {
      performSearch(value)
    }, 300)
  }

  // 키보드 네비게이션
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!isOpen) return

    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault()
        setSelectedIndex(prev =>
          prev < results.length - 1 ? prev + 1 : prev
        )
        break
      case 'ArrowUp':
        e.preventDefault()
        setSelectedIndex(prev => (prev > 0 ? prev - 1 : -1))
        break
      case 'Enter':
        e.preventDefault()
        if (selectedIndex >= 0 && results[selectedIndex]) {
          handleSelect(results[selectedIndex])
        }
        break
      case 'Escape':
        setIsOpen(false)
        setSelectedIndex(-1)
        break
    }
  }

  // 종목 선택 핸들러
  const handleSelect = (stock: StockSearchResult) => {
    setQuery(stock.name)
    setIsOpen(false)
    setResults([])
    onSelect(stock)
  }

  // 인기 검색어 클릭 핸들러
  const handlePopularClick = (term: string) => {
    setQuery(term)
    performSearch(term)
  }

  // 외부 클릭 감지
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(event.target as Node) &&
        inputRef.current &&
        !inputRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false)
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  // 등락률 색상
  const getChangeColor = (rate: number | null) => {
    if (rate === null) return 'text-gray-500'
    return rate >= 0 ? 'text-red-500' : 'text-blue-500'
  }

  // 시가총액 포맷
  const formatMarketCap = (cap: number | null) => {
    if (!cap) return '-'
    if (cap >= 1_000_000_000_000) {
      return `${(cap / 1_000_000_000_000).toFixed(1)}조`
    }
    if (cap >= 100_000_000) {
      return `${Math.round(cap / 100_000_000)}억`
    }
    return `${cap.toLocaleString()}원`
  }

  return (
    <div className="relative w-full max-w-md">
      {/* 검색 입력 */}
      <div className="relative">
        <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
          <svg
            className="h-5 w-5 text-gray-400"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
            />
          </svg>
        </div>
        <input
          ref={inputRef}
          type="text"
          value={query}
          onChange={handleInputChange}
          onKeyDown={handleKeyDown}
          onFocus={() => setIsOpen(true)}
          placeholder={placeholder}
          className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent bg-white text-gray-900"
        />
        {isLoading && (
          <div className="absolute inset-y-0 right-0 pr-3 flex items-center">
            <div className="animate-spin h-5 w-5 border-2 border-blue-500 border-t-transparent rounded-full"></div>
          </div>
        )}
      </div>

      {/* 검색 결과 드롭다운 */}
      {isOpen && (
        <div
          ref={dropdownRef}
          className="absolute z-50 w-full mt-1 bg-white border border-gray-200 rounded-lg shadow-lg max-h-96 overflow-y-auto"
        >
          {/* 검색 결과 */}
          {results.length > 0 ? (
            <ul className="divide-y divide-gray-100">
              {results.map((stock, index) => (
                <li
                  key={stock.code}
                  onClick={() => handleSelect(stock)}
                  className={`px-4 py-3 cursor-pointer transition-colors ${
                    index === selectedIndex
                      ? 'bg-blue-50'
                      : 'hover:bg-gray-50'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-medium text-gray-900">
                          {stock.name}
                        </span>
                        <span className="text-xs text-gray-400">
                          {stock.code}
                        </span>
                        <span className="text-xs px-1.5 py-0.5 bg-gray-100 text-gray-600 rounded">
                          {stock.market}
                        </span>
                      </div>
                      <div className="flex items-center gap-3 mt-1 text-sm">
                        <span className="text-gray-600">
                          {stock.currentPrice?.toLocaleString()}원
                        </span>
                        <span className={getChangeColor(stock.changeRate)}>
                          {stock.changeRate !== null &&
                            `${stock.changeRate >= 0 ? '+' : ''}${stock.changeRate.toFixed(2)}%`}
                        </span>
                        <span className="text-gray-400">
                          시총 {formatMarketCap(stock.marketCap)}
                        </span>
                      </div>
                    </div>
                    {stock.aiScore && (
                      <div className="text-right">
                        <span
                          className={`text-lg font-bold ${
                            stock.aiScore >= 70
                              ? 'text-green-500'
                              : stock.aiScore >= 50
                              ? 'text-yellow-500'
                              : 'text-red-500'
                          }`}
                        >
                          {stock.aiScore}
                        </span>
                        <p className="text-xs text-gray-400">AI점수</p>
                      </div>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          ) : query.trim() && !isLoading ? (
            <div className="px-4 py-6 text-center text-gray-500">
              <p>검색 결과가 없습니다</p>
              <p className="text-sm mt-1">다른 검색어를 입력해 주세요</p>
            </div>
          ) : !query.trim() && popularTerms.length > 0 ? (
            <div className="p-4">
              <p className="text-xs text-gray-500 mb-2">인기 검색어</p>
              <div className="flex flex-wrap gap-2">
                {popularTerms.slice(0, 8).map((term, index) => (
                  <button
                    key={index}
                    onClick={() => handlePopularClick(term)}
                    className="px-3 py-1 text-sm bg-gray-100 text-gray-700 rounded-full hover:bg-gray-200 transition-colors"
                  >
                    {term}
                  </button>
                ))}
              </div>
            </div>
          ) : null}
        </div>
      )}
    </div>
  )
}

export default StockSearch
