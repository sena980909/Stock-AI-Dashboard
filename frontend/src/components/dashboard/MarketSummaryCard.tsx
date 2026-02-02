import { useEffect, useState } from 'react'
import { marketApi } from '../../services/api'

interface MarketSummary {
  kospiIndex: number
  kospiChange: number
  kospiChangePercent: number
  kosdaqIndex: number
  kosdaqChange: number
  kosdaqChangePercent: number
  marketStatus: string
  marketAnalysis: string
  topSectors: { name: string; changePercent: number }[]
  bottomSectors: { name: string; changePercent: number }[]
  keyIssues: string[]
  updatedAt: string
}

function MarketSummaryCard() {
  const [summary, setSummary] = useState<MarketSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchMarketSummary()
    // 5분마다 갱신
    const interval = setInterval(fetchMarketSummary, 300000)
    return () => clearInterval(interval)
  }, [])

  const fetchMarketSummary = async () => {
    try {
      const data = await marketApi.getMarketSummary()
      setSummary(data)
      setError(null)
    } catch (err) {
      console.error('Failed to fetch market summary:', err)
      setError('시장 정보를 불러오지 못했습니다')
    } finally {
      setLoading(false)
    }
  }

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'STRONG_UP': return 'text-red-600 bg-red-100'
      case 'UP': return 'text-red-500 bg-red-50'
      case 'FLAT': return 'text-gray-600 bg-gray-100'
      case 'DOWN': return 'text-blue-500 bg-blue-50'
      case 'STRONG_DOWN': return 'text-blue-600 bg-blue-100'
      default: return 'text-gray-600 bg-gray-100'
    }
  }

  const getStatusText = (status: string) => {
    switch (status) {
      case 'STRONG_UP': return '강세'
      case 'UP': return '상승'
      case 'FLAT': return '보합'
      case 'DOWN': return '하락'
      case 'STRONG_DOWN': return '약세'
      default: return '분석중'
    }
  }

  const formatChange = (value: number) => {
    const sign = value >= 0 ? '+' : ''
    return `${sign}${value.toFixed(1)}%`
  }

  if (loading) {
    return (
      <div className="bg-white rounded-lg shadow-md p-4 mb-6 animate-pulse">
        <div className="flex items-center gap-2 mb-4">
          <div className="w-6 h-6 bg-gray-200 rounded"></div>
          <div className="w-32 h-6 bg-gray-200 rounded"></div>
        </div>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map(i => (
            <div key={i} className="h-20 bg-gray-100 rounded-lg"></div>
          ))}
        </div>
      </div>
    )
  }

  if (error || !summary) {
    return (
      <div className="bg-white rounded-lg shadow-md p-4 mb-6">
        <div className="flex items-center gap-2 text-gray-500">
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span>{error || '시장 정보를 불러오지 못했습니다'}</span>
        </div>
      </div>
    )
  }

  return (
    <div className="bg-white rounded-lg shadow-md p-4 mb-6">
      {/* 헤더 */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <svg className="w-5 h-5 text-amber-500" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clipRule="evenodd" />
          </svg>
          <h2 className="text-lg font-bold text-gray-800">오늘의 시장</h2>
          <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${getStatusColor(summary.marketStatus)}`}>
            {getStatusText(summary.marketStatus)}
          </span>
        </div>
        <span className="text-xs text-gray-400">{summary.updatedAt} 기준</span>
      </div>

      {/* 지수 카드 */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-4">
        {/* 코스피 */}
        <div className="bg-gray-50 rounded-lg p-3">
          <div className="text-sm text-gray-500 mb-1">코스피</div>
          <div className="text-xl font-bold text-gray-800">{summary.kospiIndex.toLocaleString()}</div>
          <div className={`text-sm font-medium ${summary.kospiChange >= 0 ? 'text-red-500' : 'text-blue-500'}`}>
            {summary.kospiChange >= 0 ? '▲' : '▼'} {Math.abs(summary.kospiChange)} ({formatChange(summary.kospiChangePercent)})
          </div>
        </div>

        {/* 코스닥 */}
        <div className="bg-gray-50 rounded-lg p-3">
          <div className="text-sm text-gray-500 mb-1">코스닥</div>
          <div className="text-xl font-bold text-gray-800">{summary.kosdaqIndex.toLocaleString()}</div>
          <div className={`text-sm font-medium ${summary.kosdaqChange >= 0 ? 'text-red-500' : 'text-blue-500'}`}>
            {summary.kosdaqChange >= 0 ? '▲' : '▼'} {Math.abs(summary.kosdaqChange)} ({formatChange(summary.kosdaqChangePercent)})
          </div>
        </div>

        {/* 강세 업종 */}
        <div className="bg-red-50 rounded-lg p-3">
          <div className="text-sm text-red-600 mb-1 font-medium">강세 업종</div>
          <div className="space-y-1">
            {summary.topSectors.slice(0, 2).map((sector, idx) => (
              <div key={idx} className="flex justify-between text-sm">
                <span className="text-gray-700">{sector.name}</span>
                <span className="text-red-500 font-medium">{formatChange(sector.changePercent)}</span>
              </div>
            ))}
          </div>
        </div>

        {/* 약세 업종 */}
        <div className="bg-blue-50 rounded-lg p-3">
          <div className="text-sm text-blue-600 mb-1 font-medium">약세 업종</div>
          <div className="space-y-1">
            {summary.bottomSectors.slice(-2).map((sector, idx) => (
              <div key={idx} className="flex justify-between text-sm">
                <span className="text-gray-700">{sector.name}</span>
                <span className="text-blue-500 font-medium">{formatChange(sector.changePercent)}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* AI 분석 & 주요 이슈 */}
      <div className="bg-gradient-to-r from-purple-50 to-indigo-50 rounded-lg p-3">
        <div className="flex items-start gap-2">
          <div className="bg-purple-100 rounded-full p-1.5 mt-0.5">
            <svg className="w-4 h-4 text-purple-600" fill="currentColor" viewBox="0 0 20 20">
              <path d="M9 4.804A7.968 7.968 0 005.5 4c-1.255 0-2.443.29-3.5.804v10A7.969 7.969 0 015.5 14c1.669 0 3.218.51 4.5 1.385A7.962 7.962 0 0114.5 14c1.255 0 2.443.29 3.5.804v-10A7.968 7.968 0 0014.5 4c-1.255 0-2.443.29-3.5.804V12a1 1 0 11-2 0V4.804z" />
            </svg>
          </div>
          <div className="flex-1">
            <div className="text-sm font-medium text-purple-800 mb-1">AI 시장 분석</div>
            <p className="text-sm text-gray-700 mb-2">{summary.marketAnalysis}</p>
            <div className="flex flex-wrap gap-2">
              {summary.keyIssues.map((issue, idx) => (
                <span key={idx} className="text-xs bg-white/70 text-gray-600 px-2 py-1 rounded-full">
                  #{issue}
                </span>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default MarketSummaryCard
