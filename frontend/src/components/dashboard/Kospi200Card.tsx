import React from 'react'
import { StockSearchResult } from '../../types'

interface Kospi200CardProps {
  stock: StockSearchResult
  rank: number
  isFavorite: boolean
  onFavoriteToggle: (stockCode: string, stockName: string) => void
  onClick?: (stockCode: string) => void
}

const Kospi200Card: React.FC<Kospi200CardProps> = ({
  stock,
  rank,
  isFavorite,
  onFavoriteToggle,
  onClick
}) => {
  const getSignalColor = (signalType: string | null) => {
    switch (signalType) {
      case 'STRONG_BUY':
        return 'bg-green-500'
      case 'BUY':
        return 'bg-green-400'
      case 'NEUTRAL':
        return 'bg-gray-400'
      case 'SELL':
        return 'bg-red-400'
      case 'STRONG_SELL':
        return 'bg-red-500'
      default:
        return 'bg-gray-300'
    }
  }

  const getSignalText = (signalType: string | null) => {
    switch (signalType) {
      case 'STRONG_BUY':
        return '강력매수'
      case 'BUY':
        return '매수'
      case 'NEUTRAL':
        return '중립'
      case 'SELL':
        return '매도'
      case 'STRONG_SELL':
        return '강력매도'
      default:
        return '-'
    }
  }

  const formatPrice = (price: number | null) => {
    if (price === null) return '-'
    return price.toLocaleString() + '원'
  }

  const formatMarketCap = (cap: number | null) => {
    if (cap === null) return '-'
    if (cap >= 1_000_000_000_000) {
      return (cap / 1_000_000_000_000).toFixed(1) + '조'
    }
    if (cap >= 100_000_000) {
      return Math.round(cap / 100_000_000).toLocaleString() + '억'
    }
    return cap.toLocaleString() + '원'
  }

  const handleCardClick = () => {
    if (onClick) {
      onClick(stock.code)
    }
  }

  const handleFavoriteClick = (e: React.MouseEvent) => {
    e.stopPropagation()
    onFavoriteToggle(stock.code, stock.name)
  }

  return (
    <div
      onClick={handleCardClick}
      className="relative bg-white border border-gray-200 rounded-lg p-4 hover:shadow-lg hover:border-blue-300 transition-all cursor-pointer group"
    >
      {/* Rank Badge */}
      <div className="absolute -top-2 -left-2 w-7 h-7 bg-blue-500 text-white rounded-full flex items-center justify-center text-xs font-bold">
        {rank}
      </div>

      {/* Favorite Button */}
      <button
        onClick={handleFavoriteClick}
        className={`absolute top-2 right-2 p-1 rounded-full transition-colors ${
          isFavorite
            ? 'text-yellow-400 hover:text-yellow-500'
            : 'text-gray-300 hover:text-yellow-400'
        }`}
      >
        <svg
          className={`w-5 h-5 ${isFavorite ? 'fill-current' : 'stroke-current fill-none'}`}
          viewBox="0 0 20 20"
          strokeWidth={1.5}
        >
          <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
        </svg>
      </button>

      {/* Stock Info */}
      <div className="mt-2">
        <h3 className="font-semibold text-gray-900 truncate pr-6" title={stock.name}>
          {stock.name}
        </h3>
        <p className="text-xs text-gray-400 mt-0.5">{stock.code}</p>
      </div>

      {/* Price */}
      <div className="mt-3">
        <p className="text-lg font-bold text-gray-900">
          {formatPrice(stock.currentPrice)}
        </p>
        <p className={`text-sm font-medium ${
          (stock.changeRate ?? 0) >= 0 ? 'text-red-500' : 'text-blue-500'
        }`}>
          {stock.changeRate !== null
            ? `${stock.changeRate >= 0 ? '+' : ''}${stock.changeRate.toFixed(2)}%`
            : '-'
          }
        </p>
      </div>

      {/* Market Cap */}
      <p className="text-xs text-gray-500 mt-2">
        시총 {formatMarketCap(stock.marketCap)}
      </p>

      {/* Signal & AI Score */}
      <div className="mt-3 flex items-center justify-between">
        <div className="flex items-center gap-1">
          <span className={`w-2 h-2 rounded-full ${getSignalColor(stock.signalType)}`}></span>
          <span className="text-xs text-gray-600">{getSignalText(stock.signalType)}</span>
        </div>
        {stock.aiScore !== null && (
          <div className="text-right">
            <span className={`text-sm font-bold ${
              stock.aiScore >= 70 ? 'text-green-500' :
              stock.aiScore >= 50 ? 'text-gray-600' : 'text-red-500'
            }`}>
              {stock.aiScore}
            </span>
            <span className="text-xs text-gray-400 ml-0.5">점</span>
          </div>
        )}
      </div>
    </div>
  )
}

export default Kospi200Card
