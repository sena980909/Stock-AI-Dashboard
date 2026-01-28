import { Link } from 'react-router-dom'
import { Stock } from '../../types'

interface StockListProps {
  stocks: Stock[]
}

function StockList({ stocks }: StockListProps) {
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
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default StockList
