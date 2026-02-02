import React from 'react'
import { TopStock } from '../../types'

interface TopRankCardProps {
  stock: TopStock
  onClick?: (stockCode: string) => void
}

const TopRankCard: React.FC<TopRankCardProps> = ({ stock, onClick }) => {
  const getSignalColor = (signalType: string) => {
    switch (signalType) {
      case 'STRONG_BUY':
        return 'bg-green-500 text-white'
      case 'BUY':
        return 'bg-green-400 text-white'
      case 'NEUTRAL':
        return 'bg-gray-400 text-white'
      case 'SELL':
        return 'bg-red-400 text-white'
      case 'STRONG_SELL':
        return 'bg-red-500 text-white'
      default:
        return 'bg-gray-400 text-white'
    }
  }

  const getSignalLabel = (signalType: string) => {
    switch (signalType) {
      case 'STRONG_BUY':
        return '강력 매수'
      case 'BUY':
        return '매수'
      case 'NEUTRAL':
        return '중립'
      case 'SELL':
        return '매도'
      case 'STRONG_SELL':
        return '강력 매도'
      default:
        return '중립'
    }
  }

  const getScoreColor = (score: number) => {
    if (score >= 70) return 'text-green-500'
    if (score >= 50) return 'text-yellow-500'
    return 'text-red-500'
  }

  const formatMarketCap = (marketCap: number) => {
    if (marketCap >= 1_000_000_000_000) {
      return `${(marketCap / 1_000_000_000_000).toFixed(1)}조`
    } else if (marketCap >= 100_000_000) {
      return `${Math.round(marketCap / 100_000_000)}억`
    }
    return `${marketCap.toLocaleString()}원`
  }

  const formatPrice = (price: number) => {
    return price.toLocaleString() + '원'
  }

  return (
    <div
      className="bg-white rounded-lg shadow-md p-4 hover:shadow-lg transition-shadow cursor-pointer border border-gray-100"
      onClick={() => onClick?.(stock.stockCode)}
    >
      {/* Header: Rank & Stock Info */}
      <div className="flex items-start justify-between mb-3">
        <div className="flex items-center gap-3">
          <span className="text-2xl font-bold text-gray-300">
            {stock.rank}
          </span>
          <div>
            <h3 className="font-semibold text-gray-900">{stock.stockName}</h3>
            <p className="text-sm text-gray-500">{stock.stockCode}</p>
          </div>
        </div>
        <span className={`px-2 py-1 rounded text-xs font-medium ${getSignalColor(stock.signalType)}`}>
          {getSignalLabel(stock.signalType)}
        </span>
      </div>

      {/* Price & Change */}
      <div className="flex items-baseline gap-2 mb-3">
        <span className="text-xl font-bold text-gray-900">
          {formatPrice(stock.currentPrice)}
        </span>
        <span className={`text-sm font-medium ${stock.changePercent >= 0 ? 'text-red-500' : 'text-blue-500'}`}>
          {stock.changePercent >= 0 ? '+' : ''}{stock.changePercent.toFixed(2)}%
        </span>
      </div>

      {/* AI Score */}
      <div className="flex items-center justify-between mb-3">
        <span className="text-sm text-gray-500">AI 투자 점수</span>
        <div className="flex items-center gap-2">
          <div className="w-24 h-2 bg-gray-200 rounded-full overflow-hidden">
            <div
              className={`h-full rounded-full ${
                stock.aiScore >= 70 ? 'bg-green-500' :
                stock.aiScore >= 50 ? 'bg-yellow-500' : 'bg-red-500'
              }`}
              style={{ width: `${stock.aiScore}%` }}
            />
          </div>
          <span className={`text-lg font-bold ${getScoreColor(stock.aiScore)}`}>
            {stock.aiScore}
          </span>
        </div>
      </div>

      {/* Market Cap */}
      <div className="flex items-center justify-between mb-3 text-sm">
        <span className="text-gray-500">시가총액</span>
        <span className="font-medium text-gray-700">{formatMarketCap(stock.marketCap)}</span>
      </div>

      {/* AI Summary */}
      <div className="bg-gray-50 rounded p-2">
        <p className="text-sm text-gray-600 line-clamp-2">
          {stock.summary}
        </p>
      </div>
    </div>
  )
}

export default TopRankCard
