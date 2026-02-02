import { useState, useEffect, useCallback } from 'react'
import { News } from '../../types'
import { newsApi } from '../../services/api'
import NewsModal from './NewsModal'

function NewsFeed() {
  const [news, setNews] = useState<News[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
  const [selectedNews, setSelectedNews] = useState<News | null>(null)

  const fetchNews = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await newsApi.getLatestNews()
      setNews(data)
      setLastUpdated(new Date())
    } catch (err) {
      console.error('Failed to fetch news:', err)
      setError('뉴스를 불러오는데 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchNews()

    // 5분마다 자동 갱신
    const interval = setInterval(fetchNews, 5 * 60 * 1000)
    return () => clearInterval(interval)
  }, [fetchNews])

  const handleRefresh = async () => {
    try {
      await newsApi.refreshNews()
      await fetchNews()
    } catch (err) {
      console.error('Failed to refresh news:', err)
    }
  }

  const formatTime = (dateStr: string) => {
    try {
      const date = new Date(dateStr)
      const now = new Date()
      const diff = now.getTime() - date.getTime()
      const minutes = Math.floor(diff / 60000)
      const hours = Math.floor(diff / 3600000)

      if (minutes < 1) return '방금 전'
      if (minutes < 60) return `${minutes}분 전`
      if (hours < 24) return `${hours}시간 전`
      return date.toLocaleDateString('ko-KR')
    } catch {
      return dateStr
    }
  }

  if (loading && news.length === 0) {
    return (
      <div className="bg-white rounded-lg shadow">
        <div className="px-6 py-4 border-b">
          <h2 className="text-xl font-semibold">실시간 뉴스</h2>
        </div>
        <div className="p-4 space-y-4">
          {[...Array(5)].map((_, i) => (
            <div key={i} className="animate-pulse space-y-2">
              <div className="h-4 bg-gray-200 rounded w-3/4"></div>
              <div className="h-3 bg-gray-200 rounded w-full"></div>
              <div className="h-3 bg-gray-200 rounded w-1/4"></div>
            </div>
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className="bg-white rounded-lg shadow">
      <div className="px-6 py-4 border-b flex items-center justify-between">
        <div>
          <h2 className="text-xl font-semibold">실시간 뉴스</h2>
          {lastUpdated && (
            <p className="text-xs text-gray-400 mt-1">
              {lastUpdated.toLocaleTimeString()} 업데이트
            </p>
          )}
        </div>
        <button
          onClick={handleRefresh}
          disabled={loading}
          className="p-1.5 text-gray-400 hover:text-blue-500 transition-colors disabled:opacity-50"
          title="새로고침"
        >
          <svg className={`w-5 h-5 ${loading ? 'animate-spin' : ''}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
        </button>
      </div>

      {error && (
        <div className="px-4 py-3 bg-red-50 text-red-600 text-sm">
          {error}
          <button onClick={fetchNews} className="ml-2 underline">다시 시도</button>
        </div>
      )}

      <div className="divide-y max-h-[600px] overflow-y-auto">
        {news.length === 0 && !loading ? (
          <div className="p-8 text-center text-gray-500">
            <p>표시할 뉴스가 없습니다.</p>
            <button onClick={fetchNews} className="mt-2 text-blue-500 hover:underline">
              새로고침
            </button>
          </div>
        ) : (
          news.map((item) => (
            <div
              key={item.id}
              className="p-4 hover:bg-gray-50 transition-colors cursor-pointer"
              onClick={() => setSelectedNews(item)}
            >
              <div className="flex items-start justify-between gap-2">
                <h3 className="font-medium text-gray-900 line-clamp-2 text-sm flex-1">
                  {item.title}
                </h3>
                <span className={`shrink-0 inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                  item.sentiment === 'positive' ? 'bg-green-100 text-green-700' :
                  item.sentiment === 'negative' ? 'bg-red-100 text-red-700' :
                  'bg-gray-100 text-gray-700'
                }`}>
                  {item.sentiment === 'positive' ? '호재' :
                   item.sentiment === 'negative' ? '악재' : '중립'}
                </span>
              </div>
              <p className="mt-1 text-sm text-gray-500 line-clamp-2">{item.summary}</p>
              <div className="mt-2 flex items-center justify-between text-xs text-gray-400">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{item.source}</span>
                  <span>|</span>
                  <span>{formatTime(item.publishedAt)}</span>
                </div>
                {item.relatedStocks && item.relatedStocks.length > 0 && (
                  <div className="flex gap-1">
                    {item.relatedStocks.slice(0, 2).map((code) => (
                      <span key={code} className="px-1.5 py-0.5 bg-blue-50 text-blue-600 rounded">
                        {code}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ))
        )}
      </div>

      {/* News Detail Modal */}
      {selectedNews && (
        <NewsModal
          news={selectedNews}
          onClose={() => setSelectedNews(null)}
        />
      )}
    </div>
  )
}

export default NewsFeed
