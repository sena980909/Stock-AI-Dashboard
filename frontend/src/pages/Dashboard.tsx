import { useEffect, useState } from 'react'
import StockList from '../components/dashboard/StockList'
import NewsFeed from '../components/dashboard/NewsFeed'
import HitRateCard from '../components/dashboard/HitRateCard'
import WatchlistCard from '../components/dashboard/WatchlistCard'
import Kospi200List from '../components/dashboard/Kospi200List'
import AiReportModal from '../components/dashboard/AiReportModal'
import StockSearch from '../components/common/StockSearch'
import { useStockWebSocket } from '../hooks/useStockWebSocket'
import { Stock, StockSearchResult } from '../types'
import { stockApi } from '../services/api'

function Dashboard() {
  const { stocks: wsStocks, isConnected } = useStockWebSocket()
  const [apiStocks, setApiStocks] = useState<Stock[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedStockCode, setSelectedStockCode] = useState<string | null>(null)

  useEffect(() => {
    const fetchStocks = async () => {
      try {
        const data = await stockApi.getRecommendedStocks()
        setApiStocks(data)
      } catch (err) {
        console.error('Failed to fetch stocks:', err)
      } finally {
        setLoading(false)
      }
    }
    fetchStocks()
    const interval = setInterval(fetchStocks, 30000)
    return () => clearInterval(interval)
  }, [])

  const displayStocks = wsStocks.length > 0 ? wsStocks : apiStocks

  const handleStockClick = (stockCode: string) => {
    setSelectedStockCode(stockCode)
  }

  const handleModalClose = () => {
    setSelectedStockCode(null)
  }

  // 검색 결과 선택 시 AI 리포트 모달 표시
  const handleSearchSelect = (stock: StockSearchResult) => {
    setSelectedStockCode(stock.code)
  }

  return (
    <div className="min-h-screen bg-gray-100">
      <div className="container mx-auto px-4 py-8">
        {/* Header with Search */}
        <header className="mb-8">
          <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
            <div>
              <h1 className="text-3xl font-bold text-gray-800">Stock AI Dashboard</h1>
              <p className="text-gray-500 mt-1">AI 기반 주식 분석 및 추천 서비스</p>
              <div className="flex items-center mt-2">
                <span className={`w-3 h-3 rounded-full mr-2 ${isConnected ? 'bg-green-500' : 'bg-red-500'}`}></span>
                <span className="text-sm text-gray-600">
                  {isConnected ? '실시간 연결됨' : '연결 끊김'}
                </span>
              </div>
            </div>

            {/* Search Component */}
            <div className="w-full md:w-auto">
              <StockSearch
                onSelect={handleSearchSelect}
                placeholder="종목명 또는 코드 검색 (예: 삼성전자, 005930)"
              />
            </div>
          </div>
        </header>

        {/* 코스피 200 섹션 */}
        <section className="mb-8">
          <Kospi200List onStockClick={handleStockClick} />
        </section>

        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
          {/* 메인 영역: AI 추천 종목 */}
          <div className="lg:col-span-2">
            <div className="bg-white rounded-lg shadow-md p-4 mb-4">
              <h2 className="text-lg font-bold text-gray-800 mb-4">AI 추천 종목</h2>
              {loading ? (
                <div className="p-8 text-center text-gray-500">
                  주가 데이터 로딩 중...
                </div>
              ) : (
                <StockList stocks={displayStocks} />
              )}
            </div>
          </div>

          {/* 사이드바 1: 관심종목 + 적중률 */}
          <div className="space-y-6">
            <WatchlistCard />
            <HitRateCard />
          </div>

          {/* 사이드바 2: 뉴스 */}
          <div>
            <NewsFeed />
          </div>
        </div>
      </div>

      {/* AI Report Modal */}
      {selectedStockCode && (
        <AiReportModal
          stockCode={selectedStockCode}
          onClose={handleModalClose}
        />
      )}
    </div>
  )
}

export default Dashboard
