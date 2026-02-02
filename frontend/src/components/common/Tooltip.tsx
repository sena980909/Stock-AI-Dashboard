import { useState, useRef, useEffect } from 'react'

interface TooltipProps {
  children: React.ReactNode
  content: string
  title?: string
}

function Tooltip({ children, content, title }: TooltipProps) {
  const [isVisible, setIsVisible] = useState(false)
  const [position, setPosition] = useState({ top: 0, left: 0 })
  const triggerRef = useRef<HTMLSpanElement>(null)
  const tooltipRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (isVisible && triggerRef.current && tooltipRef.current) {
      const triggerRect = triggerRef.current.getBoundingClientRect()
      const tooltipRect = tooltipRef.current.getBoundingClientRect()

      let top = triggerRect.bottom + 8
      let left = triggerRect.left + triggerRect.width / 2 - tooltipRect.width / 2

      // 화면 오른쪽 넘어가면 조정
      if (left + tooltipRect.width > window.innerWidth - 16) {
        left = window.innerWidth - tooltipRect.width - 16
      }
      // 화면 왼쪽 넘어가면 조정
      if (left < 16) {
        left = 16
      }

      setPosition({ top, left })
    }
  }, [isVisible])

  return (
    <span className="relative inline-flex items-center">
      <span
        ref={triggerRef}
        onMouseEnter={() => setIsVisible(true)}
        onMouseLeave={() => setIsVisible(false)}
        className="cursor-help border-b border-dotted border-gray-400"
      >
        {children}
      </span>
      {isVisible && (
        <div
          ref={tooltipRef}
          className="fixed z-50 max-w-xs bg-gray-900 text-white text-sm rounded-lg shadow-lg p-3"
          style={{ top: position.top, left: position.left }}
        >
          {title && <div className="font-bold mb-1 text-yellow-300">{title}</div>}
          <div className="text-gray-200 leading-relaxed">{content}</div>
          <div className="absolute -top-2 left-1/2 -translate-x-1/2 w-0 h-0 border-l-8 border-r-8 border-b-8 border-transparent border-b-gray-900"></div>
        </div>
      )}
    </span>
  )
}

// 금융 용어 사전
export const financialTerms: Record<string, { title: string; content: string }> = {
  RSI: {
    title: 'RSI (상대강도지수)',
    content: '주가의 상승과 하락 강도를 0~100으로 표시. 70 이상은 과매수(고점), 30 이하는 과매도(저점) 신호로 해석합니다.'
  },
  MACD: {
    title: 'MACD (이동평균수렴확산)',
    content: '단기(12일)와 장기(26일) 이동평균의 차이를 나타내는 지표. 시그널선 상향돌파 시 매수, 하향돌파 시 매도 신호로 해석합니다.'
  },
  PER: {
    title: 'PER (주가수익비율)',
    content: '주가를 주당순이익(EPS)으로 나눈 값. 낮을수록 저평가, 높을수록 고평가로 해석하나 업종별 차이가 있습니다.'
  },
  PBR: {
    title: 'PBR (주가순자산비율)',
    content: '주가를 주당순자산으로 나눈 값. 1 미만이면 자산가치 대비 저평가, 1 초과면 고평가로 해석합니다.'
  },
  EPS: {
    title: 'EPS (주당순이익)',
    content: '순이익을 발행주식수로 나눈 값. 기업의 수익성을 나타내며, 높을수록 좋습니다.'
  },
  ROE: {
    title: 'ROE (자기자본이익률)',
    content: '순이익을 자기자본으로 나눈 비율. 투입 자본 대비 얼마나 이익을 냈는지 보여주며, 10% 이상이면 우수합니다.'
  },
  시가총액: {
    title: '시가총액',
    content: '현재 주가 × 발행주식수. 기업의 시장 가치를 나타내며, 코스피200은 시가총액 상위 200개 종목입니다.'
  },
  외국인보유비율: {
    title: '외국인 보유비율',
    content: '외국인 투자자가 보유한 주식 비율. 높을수록 글로벌 자금의 관심이 높은 종목입니다.'
  },
  이동평균선: {
    title: '이동평균선 (MA)',
    content: '일정 기간의 종가 평균을 연결한 선. 20일선(단기), 60일선(중기), 120일선(장기)가 대표적이며, 주가 지지/저항선으로 활용합니다.'
  },
  골든크로스: {
    title: '골든크로스',
    content: '단기 이동평균선이 장기 이동평균선을 상향 돌파하는 현상. 상승 추세 전환 신호로 해석합니다.'
  },
  데드크로스: {
    title: '데드크로스',
    content: '단기 이동평균선이 장기 이동평균선을 하향 돌파하는 현상. 하락 추세 전환 신호로 해석합니다.'
  },
  거래량: {
    title: '거래량',
    content: '일정 기간 동안 거래된 주식 수. 거래량 증가와 주가 상승이 동반되면 강한 상승세, 거래량 감소는 관망세를 의미합니다.'
  }
}

// 특정 금융 용어 툴팁
export function FinancialTerm({ term }: { term: keyof typeof financialTerms }) {
  const termData = financialTerms[term]
  if (!termData) return <span>{term}</span>

  return (
    <Tooltip title={termData.title} content={termData.content}>
      {term}
    </Tooltip>
  )
}

export default Tooltip
