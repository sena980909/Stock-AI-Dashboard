import React, { useState, useEffect } from 'react'
import { AiReport } from '../../types'
import { stockApi } from '../../services/api'
import { FinancialTerm } from '../common/Tooltip'

interface AiReportModalProps {
  stockCode: string | null
  onClose: () => void
}

const AiReportModal: React.FC<AiReportModalProps> = ({ stockCode, onClose }) => {
  const [report, setReport] = useState<AiReport | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!stockCode) return

    const fetchReport = async () => {
      try {
        setLoading(true)
        setError(null)
        const data = await stockApi.getAiReport(stockCode)
        setReport(data)
      } catch (err) {
        console.error('Failed to fetch AI report:', err)
        setError('리포트를 불러오는데 실패했습니다.')
      } finally {
        setLoading(false)
      }
    }

    fetchReport()
  }, [stockCode])

  if (!stockCode) return null

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

  const getTrendIcon = (trend: string) => {
    switch (trend) {
      case 'UPTREND':
        return '📈'
      case 'DOWNTREND':
        return '📉'
      default:
        return '➡️'
    }
  }

  const formatMarketCap = (marketCap: number) => {
    if (marketCap >= 1_000_000_000_000) {
      return `${(marketCap / 1_000_000_000_000).toFixed(1)}조원`
    }
    return `${Math.round(marketCap / 100_000_000)}억원`
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black bg-opacity-50"
        onClick={onClose}
      />

      {/* Modal */}
      <div className="relative bg-white rounded-lg shadow-xl w-full max-w-4xl max-h-[90vh] overflow-y-auto m-4">
        {/* Header */}
        <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
          <h2 className="text-xl font-bold text-gray-900">
            AI 투자 리포트
          </h2>
          <button
            onClick={onClose}
            className="p-2 hover:bg-gray-100 rounded-full transition-colors"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Content */}
        <div className="p-6">
          {loading && (
            <div className="flex items-center justify-center py-12">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500"></div>
            </div>
          )}

          {error && (
            <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded">
              {error}
            </div>
          )}

          {report && !loading && (
            <div className="space-y-6">
              {/* Stock Info Header */}
              <div className="flex items-start justify-between">
                <div>
                  <div className="flex items-center gap-3">
                    <h3 className="text-2xl font-bold text-gray-900">{report.stockName}</h3>
                    {report.sector && (
                      <span className="px-2 py-1 bg-gray-100 text-gray-600 text-sm rounded-full">
                        {report.sector}
                      </span>
                    )}
                  </div>
                  <p className="text-gray-500">{report.stockCode}</p>
                </div>
                <span className={`px-4 py-2 rounded-lg text-lg font-bold ${getSignalColor(report.signalType)}`}>
                  {getSignalLabel(report.signalType)}
                </span>
              </div>

              {/* Company Description */}
              {report.companyDescription && (
                <div className="bg-gray-50 rounded-lg p-4 border-l-4 border-gray-300">
                  <p className="text-sm text-gray-600 font-medium mb-1">기업 개요</p>
                  <p className="text-gray-800">{report.companyDescription}</p>
                </div>
              )}

              {/* Summary Card */}
              <div className="bg-blue-50 rounded-lg p-4">
                <p className="text-sm text-blue-600 font-medium mb-1">AI 분석 요약</p>
                <p className="text-blue-800 font-medium">{report.summary}</p>
              </div>

              {/* Scores Grid */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {/* Total Score */}
                <div className="bg-gray-50 rounded-lg p-4 text-center">
                  <p className="text-sm text-gray-500 mb-2">종합 점수</p>
                  <p className={`text-4xl font-bold ${
                    report.totalScore >= 70 ? 'text-green-500' :
                    report.totalScore >= 50 ? 'text-yellow-500' : 'text-red-500'
                  }`}>
                    {report.totalScore}
                  </p>
                </div>

                {/* Technical Score */}
                <div className="bg-gray-50 rounded-lg p-4 text-center">
                  <p className="text-sm text-gray-500 mb-2">기술적 분석</p>
                  <p className="text-4xl font-bold text-blue-500">{report.technicalScore}</p>
                </div>

                {/* Sentiment Score */}
                <div className="bg-gray-50 rounded-lg p-4 text-center">
                  <p className="text-sm text-gray-500 mb-2">감성 분석</p>
                  <p className="text-4xl font-bold text-purple-500">{report.sentimentScore}</p>
                </div>
              </div>

              {/* Price & Market Info */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <div className="bg-gray-50 rounded-lg p-3">
                  <p className="text-xs text-gray-500">현재가</p>
                  <p className="text-lg font-bold">{report.currentPrice?.toLocaleString()}원</p>
                </div>
                <div className="bg-gray-50 rounded-lg p-3">
                  <p className="text-xs text-gray-500">등락률</p>
                  <p className={`text-lg font-bold ${report.changePercent >= 0 ? 'text-red-500' : 'text-blue-500'}`}>
                    {report.changePercent >= 0 ? '+' : ''}{report.changePercent?.toFixed(2)}%
                  </p>
                </div>
                <div className="bg-gray-50 rounded-lg p-3">
                  <p className="text-xs text-gray-500"><FinancialTerm term="시가총액" /></p>
                  <p className="text-lg font-bold">{formatMarketCap(report.marketCap)}</p>
                </div>
                <div className="bg-gray-50 rounded-lg p-3">
                  <p className="text-xs text-gray-500">분석 시간</p>
                  <p className="text-sm font-medium">
                    {report.analyzedAt ? new Date(report.analyzedAt).toLocaleString() : '-'}
                  </p>
                </div>
              </div>

              {/* SWOT Analysis */}
              {report.swot && (
                <div>
                  <h4 className="text-lg font-bold text-gray-900 mb-4">SWOT 분석</h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {/* Strengths */}
                    <div className="bg-green-50 rounded-lg p-4">
                      <h5 className="font-bold text-green-700 mb-2">강점 (Strengths)</h5>
                      <ul className="space-y-1">
                        {report.swot.strengths?.map((item, index) => (
                          <li key={index} className="text-sm text-green-600 flex items-start gap-2">
                            <span>+</span>
                            <span>{item}</span>
                          </li>
                        ))}
                      </ul>
                    </div>

                    {/* Weaknesses */}
                    <div className="bg-red-50 rounded-lg p-4">
                      <h5 className="font-bold text-red-700 mb-2">약점 (Weaknesses)</h5>
                      <ul className="space-y-1">
                        {report.swot.weaknesses?.map((item, index) => (
                          <li key={index} className="text-sm text-red-600 flex items-start gap-2">
                            <span>-</span>
                            <span>{item}</span>
                          </li>
                        ))}
                      </ul>
                    </div>

                    {/* Opportunities */}
                    <div className="bg-blue-50 rounded-lg p-4">
                      <h5 className="font-bold text-blue-700 mb-2">기회 (Opportunities)</h5>
                      <ul className="space-y-1">
                        {report.swot.opportunities?.map((item, index) => (
                          <li key={index} className="text-sm text-blue-600 flex items-start gap-2">
                            <span>*</span>
                            <span>{item}</span>
                          </li>
                        ))}
                      </ul>
                    </div>

                    {/* Threats */}
                    <div className="bg-orange-50 rounded-lg p-4">
                      <h5 className="font-bold text-orange-700 mb-2">위협 (Threats)</h5>
                      <ul className="space-y-1">
                        {report.swot.threats?.map((item, index) => (
                          <li key={index} className="text-sm text-orange-600 flex items-start gap-2">
                            <span>!</span>
                            <span>{item}</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  </div>
                </div>
              )}

              {/* Technical Indicators */}
              {report.technicalIndicators && (
                <div>
                  <h4 className="text-lg font-bold text-gray-900 mb-4">기술적 지표</h4>
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                    <div className="bg-gray-50 rounded-lg p-3">
                      <p className="text-xs text-gray-500">추세</p>
                      <p className="text-lg font-bold">
                        {getTrendIcon(report.technicalIndicators.trend)}{' '}
                        {report.technicalIndicators.trend === 'UPTREND' ? '상승' :
                         report.technicalIndicators.trend === 'DOWNTREND' ? '하락' : '횡보'}
                      </p>
                    </div>
                    <div className="bg-gray-50 rounded-lg p-3">
                      <p className="text-xs text-gray-500"><FinancialTerm term="RSI" /></p>
                      <p className="text-lg font-bold">{report.technicalIndicators.rsiValue?.toFixed(1)}</p>
                      <p className={`text-xs ${
                        report.technicalIndicators.rsiSignal === 'OVERBOUGHT' ? 'text-red-500' :
                        report.technicalIndicators.rsiSignal === 'OVERSOLD' ? 'text-blue-500' : 'text-gray-500'
                      }`}>
                        {report.technicalIndicators.rsiSignal === 'OVERBOUGHT' ? '과매수' :
                         report.technicalIndicators.rsiSignal === 'OVERSOLD' ? '과매도' : '중립'}
                      </p>
                    </div>
                    <div className="bg-gray-50 rounded-lg p-3">
                      <p className="text-xs text-gray-500"><FinancialTerm term="MACD" /> 신호</p>
                      <p className={`text-lg font-bold ${
                        report.technicalIndicators.macdSignal === 'BUY' ? 'text-green-500' :
                        report.technicalIndicators.macdSignal === 'SELL' ? 'text-red-500' : 'text-gray-500'
                      }`}>
                        {report.technicalIndicators.macdSignal === 'BUY' ? '매수' :
                         report.technicalIndicators.macdSignal === 'SELL' ? '매도' : '중립'}
                      </p>
                    </div>
                    <div className="bg-gray-50 rounded-lg p-3">
                      <p className="text-xs text-gray-500"><FinancialTerm term="이동평균선" /></p>
                      <p className={`text-lg font-bold ${
                        report.technicalIndicators.movingAverage === 'ABOVE_MA' ? 'text-green-500' : 'text-red-500'
                      }`}>
                        {report.technicalIndicators.movingAverage === 'ABOVE_MA' ? '상회' : '하회'}
                      </p>
                    </div>
                  </div>
                </div>
              )}

              {/* Sentiment Data */}
              {report.sentimentData && (
                <div>
                  <h4 className="text-lg font-bold text-gray-900 mb-4">뉴스 감성 분석</h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {/* News Count */}
                    <div className="bg-gray-50 rounded-lg p-4">
                      <div className="flex items-center justify-between mb-3">
                        <span className="text-sm text-gray-500">뉴스 감성 분포</span>
                        <span className={`text-sm font-bold ${
                          report.sentimentData.overallSentiment > 0.3 ? 'text-green-500' :
                          report.sentimentData.overallSentiment < -0.3 ? 'text-red-500' : 'text-gray-500'
                        }`}>
                          {report.sentimentData.overallSentiment > 0.3 ? '긍정적' :
                           report.sentimentData.overallSentiment < -0.3 ? '부정적' : '중립'}
                        </span>
                      </div>
                      <div className="flex gap-4">
                        <div className="text-center">
                          <p className="text-2xl font-bold text-green-500">{report.sentimentData.positiveNewsCount}</p>
                          <p className="text-xs text-gray-500">긍정</p>
                        </div>
                        <div className="text-center">
                          <p className="text-2xl font-bold text-gray-500">{report.sentimentData.neutralNewsCount}</p>
                          <p className="text-xs text-gray-500">중립</p>
                        </div>
                        <div className="text-center">
                          <p className="text-2xl font-bold text-red-500">{report.sentimentData.negativeNewsCount}</p>
                          <p className="text-xs text-gray-500">부정</p>
                        </div>
                      </div>
                    </div>

                    {/* Recent Headlines */}
                    <div className="bg-gray-50 rounded-lg p-4">
                      <p className="text-sm text-gray-500 mb-2">최근 헤드라인</p>
                      <ul className="space-y-2">
                        {report.sentimentData.recentHeadlines?.map((headline, index) => (
                          <li key={index} className="text-sm text-gray-700 flex items-start gap-2">
                            <span className="text-blue-500">•</span>
                            <span>{headline}</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default AiReportModal
