import { useEffect, useState } from 'react'
import { News } from '../../types'

const MOCK_NEWS: News[] = [
  {
    id: '1', title: '삼성전자, AI 반도체 수요 급증으로 실적 호조 전망',
    summary: '삼성전자가 AI 반도체 수요 증가로 올해 실적이 크게 개선될 것으로 전망된다.',
    sentiment: 'positive', sentimentScore: 0.85, source: '한국경제',
    publishedAt: new Date().toISOString(), relatedStocks: ['005930']
  },
  {
    id: '2', title: 'SK하이닉스, HBM3E 양산 돌입... 글로벌 수주 확대',
    summary: 'SK하이닉스가 차세대 고대역폭 메모리 HBM3E 양산을 본격화한다.',
    sentiment: 'positive', sentimentScore: 0.78, source: '매일경제',
    publishedAt: new Date(Date.now() - 3600000).toISOString(), relatedStocks: ['000660']
  },
  {
    id: '3', title: '미 연준 금리 인하 지연 우려... 코스피 하락 전망',
    summary: '미국 연방준비제도의 금리 인하 시기가 늦어질 수 있다는 전망이 나왔다.',
    sentiment: 'negative', sentimentScore: -0.62, source: '조선일보',
    publishedAt: new Date(Date.now() - 7200000).toISOString(), relatedStocks: []
  },
  {
    id: '4', title: 'NAVER, 클라우드 사업 성장세 지속',
    summary: '네이버 클라우드가 기업 고객 확대로 전년 대비 35% 성장했다.',
    sentiment: 'positive', sentimentScore: 0.65, source: '서울경제',
    publishedAt: new Date(Date.now() - 10800000).toISOString(), relatedStocks: ['035420']
  },
  {
    id: '5', title: '삼성SDI 배터리 원자재 가격 하락에 마진 악화 우려',
    summary: '리튬 가격 하락으로 삼성SDI 수익성에 부정적 영향이 예상된다.',
    sentiment: 'negative', sentimentScore: -0.55, source: '한국경제',
    publishedAt: new Date(Date.now() - 14400000).toISOString(), relatedStocks: ['006400']
  },
]

function NewsFeed() {
  const [news, setNews] = useState<News[]>(MOCK_NEWS)

  return (
    <div className="bg-white rounded-lg shadow">
      <div className="px-6 py-4 border-b">
        <h2 className="text-xl font-semibold">실시간 뉴스</h2>
      </div>
      <div className="divide-y max-h-[600px] overflow-y-auto">
        {news.map((item) => (
          <div key={item.id} className="p-4 hover:bg-gray-50">
            <div className="flex items-start justify-between">
              <h3 className="font-medium text-gray-900 line-clamp-2 text-sm">{item.title}</h3>
              <span className={`ml-2 shrink-0 inline-flex items-center px-2 py-0.5 rounded text-xs ${
                item.sentiment === 'positive' ? 'bg-green-100 text-green-700' :
                item.sentiment === 'negative' ? 'bg-red-100 text-red-700' :
                'bg-gray-100 text-gray-700'
              }`}>
                {item.sentiment === 'positive' ? '호재' :
                 item.sentiment === 'negative' ? '악재' : '중립'}
              </span>
            </div>
            <p className="mt-1 text-sm text-gray-500 line-clamp-2">{item.summary}</p>
            <div className="mt-2 flex items-center text-xs text-gray-400">
              <span>{item.source}</span>
              <span className="mx-2">|</span>
              <span>{new Date(item.publishedAt).toLocaleString('ko-KR')}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

export default NewsFeed
