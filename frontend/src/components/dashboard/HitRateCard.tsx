import { useEffect, useState } from 'react'
import axios from 'axios'

interface HitRateData {
  totalCount: number
  successCount: number
  failCount: number
  avgProfitRate: number
  hitRate: number
}

function HitRateCard() {
  const [hitRate, setHitRate] = useState<HitRateData | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const fetchHitRate = async () => {
      try {
        const response = await axios.get('/api/recommendations/hit-rate')
        setHitRate(response.data)
      } catch (err) {
        console.error('Failed to fetch hit rate:', err)
      } finally {
        setLoading(false)
      }
    }
    fetchHitRate()
  }, [])

  if (loading) {
    return (
      <div className="bg-white rounded-lg shadow p-6">
        <div className="animate-pulse h-32 bg-gray-200 rounded"></div>
      </div>
    )
  }

  if (!hitRate || hitRate.totalCount === 0) {
    return (
      <div className="bg-white rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-4">AI 적중률</h3>
        <p className="text-gray-500 text-sm">아직 분석 데이터가 없습니다</p>
      </div>
    )
  }

  return (
    <div className="bg-white rounded-lg shadow p-6">
      <h3 className="text-lg font-semibold mb-4">AI 적중률</h3>

      <div className="text-center mb-4">
        <div className={`text-4xl font-bold ${hitRate.hitRate >= 50 ? 'text-green-600' : 'text-red-600'}`}>
          {hitRate.hitRate.toFixed(1)}%
        </div>
        <div className="text-sm text-gray-500">전체 적중률</div>
      </div>

      <div className="grid grid-cols-3 gap-2 text-center text-sm">
        <div className="bg-gray-50 rounded p-2">
          <div className="font-semibold">{hitRate.totalCount}</div>
          <div className="text-gray-500">총 추천</div>
        </div>
        <div className="bg-green-50 rounded p-2">
          <div className="font-semibold text-green-600">{hitRate.successCount}</div>
          <div className="text-gray-500">성공</div>
        </div>
        <div className="bg-red-50 rounded p-2">
          <div className="font-semibold text-red-600">{hitRate.failCount}</div>
          <div className="text-gray-500">실패</div>
        </div>
      </div>

      {hitRate.avgProfitRate !== null && (
        <div className="mt-4 text-center">
          <span className="text-sm text-gray-500">평균 수익률: </span>
          <span className={`font-semibold ${hitRate.avgProfitRate >= 0 ? 'text-green-600' : 'text-red-600'}`}>
            {hitRate.avgProfitRate >= 0 ? '+' : ''}{hitRate.avgProfitRate?.toFixed(2)}%
          </span>
        </div>
      )}
    </div>
  )
}

export default HitRateCard
