import React from 'react'
import { News } from '../../types'

interface NewsModalProps {
  news: News
  onClose: () => void
}

const NewsModal: React.FC<NewsModalProps> = ({ news, onClose }) => {
  const formatTime = (dateStr: string) => {
    try {
      const date = new Date(dateStr)
      return date.toLocaleString('ko-KR', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      })
    } catch {
      return dateStr
    }
  }

  const getSentimentBadge = (sentiment: string) => {
    switch (sentiment) {
      case 'positive':
        return { text: '호재', color: 'bg-green-100 text-green-700' }
      case 'negative':
        return { text: '악재', color: 'bg-red-100 text-red-700' }
      default:
        return { text: '중립', color: 'bg-gray-100 text-gray-700' }
    }
  }

  const badge = getSentimentBadge(news.sentiment)

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black bg-opacity-50"
        onClick={onClose}
      />

      {/* Modal */}
      <div className="relative bg-white rounded-lg shadow-xl w-full max-w-2xl max-h-[90vh] overflow-hidden m-4">
        {/* Header */}
        <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4">
          <div className="flex items-start justify-between gap-4">
            <div className="flex-1">
              <div className="flex items-center gap-2 mb-2">
                <span className={`px-2 py-0.5 rounded text-xs font-medium ${badge.color}`}>
                  {badge.text}
                </span>
                <span className="text-sm text-gray-500">{news.source}</span>
              </div>
              <h2 className="text-xl font-bold text-gray-900 leading-tight">
                {news.title}
              </h2>
              <p className="text-sm text-gray-500 mt-2">
                {formatTime(news.publishedAt)}
              </p>
            </div>
            <button
              onClick={onClose}
              className="p-2 hover:bg-gray-100 rounded-full transition-colors shrink-0"
            >
              <svg className="w-5 h-5 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="p-6 overflow-y-auto max-h-[calc(90vh-180px)]">
          {/* Summary/Content */}
          <div className="prose prose-sm max-w-none">
            <p className="text-gray-700 leading-relaxed whitespace-pre-wrap">
              {news.content || news.summary}
            </p>
          </div>

          {/* Sentiment Score */}
          {news.sentimentScore !== undefined && (
            <div className="mt-6 p-4 bg-gray-50 rounded-lg">
              <h4 className="text-sm font-medium text-gray-700 mb-2">AI 감성 분석</h4>
              <div className="flex items-center gap-4">
                <div className="flex-1">
                  <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
                    <div
                      className={`h-full transition-all ${
                        news.sentimentScore > 0 ? 'bg-green-500' :
                        news.sentimentScore < 0 ? 'bg-red-500' : 'bg-gray-400'
                      }`}
                      style={{
                        width: `${Math.abs(news.sentimentScore) * 100}%`,
                        marginLeft: news.sentimentScore < 0 ? `${50 + news.sentimentScore * 50}%` : '50%'
                      }}
                    />
                  </div>
                  <div className="flex justify-between mt-1 text-xs text-gray-500">
                    <span>부정</span>
                    <span>중립</span>
                    <span>긍정</span>
                  </div>
                </div>
                <div className="text-right">
                  <span className={`text-lg font-bold ${
                    news.sentimentScore > 0.3 ? 'text-green-600' :
                    news.sentimentScore < -0.3 ? 'text-red-600' : 'text-gray-600'
                  }`}>
                    {news.sentimentScore > 0 ? '+' : ''}{(news.sentimentScore * 100).toFixed(0)}%
                  </span>
                </div>
              </div>
            </div>
          )}

          {/* Related Stocks */}
          {news.relatedStocks && news.relatedStocks.length > 0 && (
            <div className="mt-6">
              <h4 className="text-sm font-medium text-gray-700 mb-2">관련 종목</h4>
              <div className="flex flex-wrap gap-2">
                {news.relatedStocks.map((code) => (
                  <span
                    key={code}
                    className="px-3 py-1 bg-blue-50 text-blue-600 rounded-full text-sm"
                  >
                    {code}
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* Keywords */}
          {news.keywords && news.keywords.length > 0 && (
            <div className="mt-6">
              <h4 className="text-sm font-medium text-gray-700 mb-2">키워드</h4>
              <div className="flex flex-wrap gap-2">
                {news.keywords.map((keyword, index) => (
                  <span
                    key={index}
                    className="px-2 py-1 bg-gray-100 text-gray-600 rounded text-xs"
                  >
                    #{keyword}
                  </span>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="border-t border-gray-200 px-6 py-4 bg-gray-50">
          <div className="flex items-center justify-between">
            <span className="text-xs text-gray-500">
              * AI가 분석한 뉴스 요약입니다. 투자 결정 시 원문을 확인하세요.
            </span>
            <button
              onClick={onClose}
              className="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 transition-colors text-sm"
            >
              닫기
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

export default NewsModal
