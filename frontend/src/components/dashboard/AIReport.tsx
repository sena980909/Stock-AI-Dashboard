import { AIAnalysis } from '../../types'

interface AIReportProps {
  analysis: AIAnalysis | null
}

function AIReport({ analysis }: AIReportProps) {
  if (!analysis) {
    return (
      <div className="bg-white rounded-lg shadow p-6">
        <p className="text-gray-500">AI 분석 데이터를 불러오는 중...</p>
      </div>
    )
  }

  const recommendationColors = {
    buy: 'bg-green-100 text-green-800 border-green-200',
    sell: 'bg-red-100 text-red-800 border-red-200',
    hold: 'bg-yellow-100 text-yellow-800 border-yellow-200'
  }

  const recommendationText = {
    buy: '매수 추천',
    sell: '매도 추천',
    hold: '관망 추천'
  }

  return (
    <div className="bg-white rounded-lg shadow">
      <div className="px-6 py-4 border-b">
        <h2 className="text-xl font-semibold">AI 분석 리포트</h2>
      </div>

      <div className="p-6 space-y-6">
        {/* AI Score */}
        <div className="text-center">
          <div className="text-5xl font-bold text-primary">{analysis.score}</div>
          <div className="text-sm text-gray-500 mt-1">AI 점수</div>
        </div>

        {/* Recommendation */}
        <div className={`p-4 rounded-lg border ${recommendationColors[analysis.recommendation]}`}>
          <div className="text-lg font-semibold text-center">
            {recommendationText[analysis.recommendation]}
          </div>
        </div>

        {/* News Sentiment Analysis */}
        <div>
          <h3 className="font-medium mb-3">뉴스 감성 분석</h3>
          <div className="space-y-2">
            <div className="flex items-center">
              <span className="w-16 text-sm text-gray-600">긍정</span>
              <div className="flex-1 bg-gray-200 rounded-full h-2.5">
                <div
                  className="bg-green-500 h-2.5 rounded-full"
                  style={{ width: `${analysis.newsAnalysis.positive}%` }}
                ></div>
              </div>
              <span className="w-12 text-right text-sm">{analysis.newsAnalysis.positive}%</span>
            </div>
            <div className="flex items-center">
              <span className="w-16 text-sm text-gray-600">부정</span>
              <div className="flex-1 bg-gray-200 rounded-full h-2.5">
                <div
                  className="bg-red-500 h-2.5 rounded-full"
                  style={{ width: `${analysis.newsAnalysis.negative}%` }}
                ></div>
              </div>
              <span className="w-12 text-right text-sm">{analysis.newsAnalysis.negative}%</span>
            </div>
            <div className="flex items-center">
              <span className="w-16 text-sm text-gray-600">중립</span>
              <div className="flex-1 bg-gray-200 rounded-full h-2.5">
                <div
                  className="bg-gray-400 h-2.5 rounded-full"
                  style={{ width: `${analysis.newsAnalysis.neutral}%` }}
                ></div>
              </div>
              <span className="w-12 text-right text-sm">{analysis.newsAnalysis.neutral}%</span>
            </div>
          </div>
        </div>

        {/* Reasons */}
        <div>
          <h3 className="font-medium mb-3">분석 근거</h3>
          <ul className="space-y-2">
            {analysis.reasons.map((reason, index) => (
              <li key={index} className="flex items-start">
                <span className="text-primary mr-2">•</span>
                <span className="text-sm text-gray-600">{reason}</span>
              </li>
            ))}
          </ul>
        </div>

        {/* Last Updated */}
        <div className="text-xs text-gray-400 text-center">
          마지막 업데이트: {new Date(analysis.lastUpdated).toLocaleString('ko-KR')}
        </div>
      </div>
    </div>
  )
}

export default AIReport
