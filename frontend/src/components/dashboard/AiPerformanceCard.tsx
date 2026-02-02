import { useEffect, useState } from 'react'
import { reportApi, AiPerformance } from '../../services/api'

function AiPerformanceCard() {
  const [performance, setPerformance] = useState<AiPerformance | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showHistory, setShowHistory] = useState(false)

  useEffect(() => {
    fetchPerformance()
  }, [])

  const fetchPerformance = async () => {
    try {
      setLoading(true)
      const data = await reportApi.getAiPerformance(30)
      setPerformance(data)
      setError(null)
    } catch (err) {
      console.error('Failed to fetch AI performance:', err)
      setError('성과 데이터를 불러오지 못했습니다')
    } finally {
      setLoading(false)
    }
  }

  const handleGenerateSample = async () => {
    try {
      setLoading(true)
      const data = await reportApi.generateSampleData(30)
      setPerformance(data)
    } catch (err) {
      console.error('Failed to generate sample data:', err)
    } finally {
      setLoading(false)
    }
  }

  const formatReturn = (value: number | null | undefined) => {
    if (value === null || value === undefined) return 'N/A'
    const sign = value >= 0 ? '+' : ''
    return `${sign}${value.toFixed(2)}%`
  }

  const formatPrice = (price: number | null | undefined) => {
    if (!price) return '-'
    return price.toLocaleString() + '원'
  }

  if (loading) {
    return (
      <div className="bg-white rounded-lg shadow-md p-6 animate-pulse">
        <div className="h-6 bg-gray-200 rounded w-1/3 mb-4"></div>
        <div className="grid grid-cols-2 gap-4">
          <div className="h-20 bg-gray-100 rounded"></div>
          <div className="h-20 bg-gray-100 rounded"></div>
        </div>
      </div>
    )
  }

  if (error || !performance) {
    return (
      <div className="bg-white rounded-lg shadow-md p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-bold text-gray-800">AI 추천 성과</h3>
        </div>
        <div className="text-center py-8 text-gray-500">
          <p className="mb-4">{error || '데이터가 없습니다'}</p>
          <button
            onClick={handleGenerateSample}
            className="px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition-colors text-sm"
          >
            샘플 데이터 생성
          </button>
        </div>
      </div>
    )
  }

  const hasData = performance.totalCount > 0

  return (
    <div className="bg-white rounded-lg shadow-md overflow-hidden">
      {/* 헤더 */}
      <div className="bg-gradient-to-r from-emerald-500 to-teal-600 p-4 text-white">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
            </svg>
            <h3 className="text-lg font-bold">AI 추천 성과 증명</h3>
          </div>
          <span className="text-xs bg-white/20 px-2 py-1 rounded-full">
            최근 {performance.periodDays}일
          </span>
        </div>
        <p className="text-sm text-emerald-100 mt-1">
          "말로만 AI 추천? 우리는 데이터로 증명합니다"
        </p>
      </div>

      {!hasData ? (
        <div className="p-6 text-center">
          <p className="text-gray-500 mb-4">아직 성과 데이터가 없습니다</p>
          <button
            onClick={handleGenerateSample}
            className="px-4 py-2 bg-emerald-500 text-white rounded-lg hover:bg-emerald-600 transition-colors text-sm"
          >
            샘플 데이터 생성 (30일)
          </button>
        </div>
      ) : (
        <div className="p-4">
          {/* 핵심 지표 */}
          <div className="grid grid-cols-2 gap-3 mb-4">
            {/* 적중률 */}
            <div className="bg-gradient-to-br from-blue-50 to-blue-100 rounded-lg p-4 text-center">
              <p className="text-xs text-blue-600 font-medium mb-1">적중률</p>
              <p className="text-3xl font-bold text-blue-700">
                {performance.hitRate.toFixed(1)}%
              </p>
              <p className="text-xs text-blue-500 mt-1">
                {performance.successCount}/{performance.totalCount} 종목
              </p>
            </div>

            {/* 평균 수익률 */}
            <div className={`rounded-lg p-4 text-center ${
              performance.averageReturn >= 0
                ? 'bg-gradient-to-br from-red-50 to-red-100'
                : 'bg-gradient-to-br from-blue-50 to-blue-100'
            }`}>
              <p className={`text-xs font-medium mb-1 ${
                performance.averageReturn >= 0 ? 'text-red-600' : 'text-blue-600'
              }`}>평균 수익률</p>
              <p className={`text-3xl font-bold ${
                performance.averageReturn >= 0 ? 'text-red-600' : 'text-blue-600'
              }`}>
                {formatReturn(performance.averageReturn)}
              </p>
              <p className={`text-xs mt-1 ${
                performance.averageReturn >= 0 ? 'text-red-400' : 'text-blue-400'
              }`}>
                연속 {performance.winStreak}연승 중
              </p>
            </div>
          </div>

          {/* 최고/최저 수익 종목 */}
          <div className="grid grid-cols-2 gap-3 mb-4">
            {/* 최고 수익 */}
            {performance.bestStock && (
              <div className="border border-green-200 rounded-lg p-3 bg-green-50">
                <div className="flex items-center gap-1 mb-2">
                  <span className="text-green-500">🏆</span>
                  <span className="text-xs text-green-600 font-medium">최고 수익</span>
                </div>
                <p className="font-bold text-gray-800 text-sm truncate">
                  {performance.bestStock.stockName}
                </p>
                <p className="text-xl font-bold text-green-600">
                  {formatReturn(performance.bestStock.returnRate)}
                </p>
                <p className="text-xs text-gray-500">
                  AI점수 {performance.bestStock.aiScore}점
                </p>
              </div>
            )}

            {/* 최저 수익 */}
            {performance.worstStock && (
              <div className="border border-red-200 rounded-lg p-3 bg-red-50">
                <div className="flex items-center gap-1 mb-2">
                  <span className="text-red-400">📉</span>
                  <span className="text-xs text-red-500 font-medium">최저 수익</span>
                </div>
                <p className="font-bold text-gray-800 text-sm truncate">
                  {performance.worstStock.stockName}
                </p>
                <p className="text-xl font-bold text-red-500">
                  {formatReturn(performance.worstStock.returnRate)}
                </p>
                <p className="text-xs text-gray-500">
                  AI점수 {performance.worstStock.aiScore}점
                </p>
              </div>
            )}
          </div>

          {/* 히스토리 토글 */}
          <button
            onClick={() => setShowHistory(!showHistory)}
            className="w-full py-2 text-sm text-gray-600 hover:text-gray-800 flex items-center justify-center gap-1 border-t pt-3"
          >
            {showHistory ? '접기' : '추천 히스토리 보기'}
            <svg
              className={`w-4 h-4 transition-transform ${showHistory ? 'rotate-180' : ''}`}
              fill="none" stroke="currentColor" viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
            </svg>
          </button>

          {/* 추천 히스토리 */}
          {showHistory && (
            <div className="mt-3 max-h-64 overflow-y-auto">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 sticky top-0">
                  <tr>
                    <th className="text-left py-2 px-2 text-xs text-gray-500">날짜</th>
                    <th className="text-left py-2 px-2 text-xs text-gray-500">종목</th>
                    <th className="text-right py-2 px-2 text-xs text-gray-500">수익률</th>
                    <th className="text-center py-2 px-2 text-xs text-gray-500">결과</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {performance.recentHistory.map((record) => (
                    <tr key={record.id} className="hover:bg-gray-50">
                      <td className="py-2 px-2 text-xs text-gray-500">
                        {new Date(record.recoDate).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })}
                      </td>
                      <td className="py-2 px-2">
                        <div className="font-medium text-gray-800 truncate max-w-[100px]">
                          {record.stockName}
                        </div>
                        <div className="text-xs text-gray-400">AI {record.aiScore}점</div>
                      </td>
                      <td className={`py-2 px-2 text-right font-mono font-bold ${
                        record.profitRate >= 0 ? 'text-red-500' : 'text-blue-500'
                      }`}>
                        {formatReturn(record.profitRate)}
                      </td>
                      <td className="py-2 px-2 text-center">
                        {record.isSuccess ? (
                          <span className="text-green-500">✅</span>
                        ) : (
                          <span className="text-red-400">❌</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* 면책 조항 */}
          <p className="text-[10px] text-gray-400 mt-4 text-center">
            * 과거 성과가 미래 수익을 보장하지 않습니다. 투자 결정은 본인 책임입니다.
          </p>
        </div>
      )}
    </div>
  )
}

export default AiPerformanceCard
