import { useState } from 'react'
import { Link } from 'react-router-dom'
import axios from 'axios'
import { Stock } from '../../types'

interface StockListProps {
  stocks: Stock[]
}

function StockList({ stocks }: StockListProps) {
  const [addedStocks, setAddedStocks] = useState<Set<string>>(new Set())
  const [addingStock, setAddingStock] = useState<string | null>(null)

  const addToWatchlist = async (stock: Stock, e: React.MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()

    if (addedStocks.has(stock.symbol)) return

    setAddingStock(stock.symbol)
    try {
      await axios.post('/api/interests', {
        stockCode: stock.symbol,
        stockName: stock.name
      })
      setAddedStocks(prev => new Set(prev).add(stock.symbol))
    } catch (err: any) {
      if (err.response?.status === 409) {
        setAddedStocks(prev => new Set(prev).add(stock.symbol))
      } else {
        console.error('Failed to add to watchlist:', err)
      }
    } finally {
      setAddingStock(null)
    }
  }

  return (
    <div className="bg-white rounded-lg shadow">
      <div className="px-6 py-4 border-b">
        <h2 className="text-xl font-semibold">AI 추천 종목</h2>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">종목</th>
              <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">현재가</th>
              <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">등락률</th>
              <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">AI 점수</th>
              <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">감성</th>
              <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">관심</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {stocks.map((stock) => (
              <tr key={stock.symbol} className="hover:bg-gray-50">
                <td className="px-6 py-4">
                  <Link to={`/stock/${stock.symbol}`} className="text-primary hover:underline">
                    <div className="font-medium">{stock.symbol}</div>
                    <div className="text-sm text-gray-500">{stock.name}</div>
                  </Link>
                </td>
                <td className="px-6 py-4 text-right font-mono">
                  {stock.price.toLocaleString()}
                </td>
                <td className={`px-6 py-4 text-right font-mono ${stock.change >= 0 ? 'text-positive' : 'text-negative'}`}>
                  {stock.change >= 0 ? '+' : ''}{stock.changePercent.toFixed(2)}%
                </td>
                <td className="px-6 py-4 text-right">
                  <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
                    stock.aiScore >= 70 ? 'bg-green-100 text-green-800' :
                    stock.aiScore >= 40 ? 'bg-yellow-100 text-yellow-800' :
                    'bg-red-100 text-red-800'
                  }`}>
                    {stock.aiScore}
                  </span>
                </td>
                <td className="px-6 py-4 text-center">
                  <span className={`inline-flex items-center px-2 py-1 rounded text-xs ${
                    stock.sentiment === 'positive' ? 'bg-green-100 text-green-700' :
                    stock.sentiment === 'negative' ? 'bg-red-100 text-red-700' :
                    'bg-gray-100 text-gray-700'
                  }`}>
                    {stock.sentiment === 'positive' ? '호재' :
                     stock.sentiment === 'negative' ? '악재' : '중립'}
                  </span>
                </td>
                <td className="px-6 py-4 text-center">
                  <button
                    onClick={(e) => addToWatchlist(stock, e)}
                    disabled={addingStock === stock.symbol || addedStocks.has(stock.symbol)}
                    className={`p-1.5 rounded-full transition-colors ${
                      addedStocks.has(stock.symbol)
                        ? 'text-yellow-500 cursor-default'
                        : 'text-gray-400 hover:text-yellow-500 hover:bg-yellow-50'
                    }`}
                    title={addedStocks.has(stock.symbol) ? '관심 종목에 추가됨' : '관심 종목에 추가'}
                  >
                    {addingStock === stock.symbol ? (
                      <svg className="w-5 h-5 animate-spin" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                      </svg>
                    ) : (
                      <svg className="w-5 h-5" fill={addedStocks.has(stock.symbol) ? 'currentColor' : 'none'} stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
                      </svg>
                    )}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default StockList
