import ReactApexChart from 'react-apexcharts'
import { ApexOptions } from 'apexcharts'
import { StockData } from '../../types'

interface CandlestickChartProps {
  data: StockData[]
}

function CandlestickChart({ data }: CandlestickChartProps) {
  if (!data || data.length === 0) {
    return (
      <div className="w-full h-[400px] flex items-center justify-center bg-gray-50 rounded">
        <p className="text-gray-500">차트 데이터가 없습니다</p>
      </div>
    )
  }

  const series = [{
    data: data.map(item => ({
      x: new Date(item.timestamp),
      y: [
        Number(item.open),
        Number(item.high),
        Number(item.low),
        Number(item.close)
      ]
    }))
  }]

  const options: ApexOptions = {
    chart: {
      type: 'candlestick',
      height: 400,
      toolbar: {
        show: true,
        tools: {
          download: true,
          selection: true,
          zoom: true,
          zoomin: true,
          zoomout: true,
          pan: true,
          reset: true
        }
      }
    },
    title: {
      text: '주가 차트',
      align: 'left'
    },
    xaxis: {
      type: 'datetime',
      labels: {
        datetimeFormatter: {
          year: 'yyyy',
          month: "MMM 'yy",
          day: 'dd MMM',
          hour: 'HH:mm'
        }
      }
    },
    yaxis: {
      tooltip: {
        enabled: true
      },
      labels: {
        formatter: (value: number) => value.toLocaleString()
      }
    },
    plotOptions: {
      candlestick: {
        colors: {
          upward: '#22c55e',
          downward: '#ef4444'
        }
      }
    },
    tooltip: {
      custom: function({ seriesIndex, dataPointIndex, w }) {
        const o = w.globals.seriesCandleO[seriesIndex][dataPointIndex]
        const h = w.globals.seriesCandleH[seriesIndex][dataPointIndex]
        const l = w.globals.seriesCandleL[seriesIndex][dataPointIndex]
        const c = w.globals.seriesCandleC[seriesIndex][dataPointIndex]
        return (
          '<div class="p-2">' +
          '<div>시가: ' + o.toLocaleString() + '</div>' +
          '<div>고가: ' + h.toLocaleString() + '</div>' +
          '<div>저가: ' + l.toLocaleString() + '</div>' +
          '<div>종가: ' + c.toLocaleString() + '</div>' +
          '</div>'
        )
      }
    }
  }

  return (
    <div className="w-full">
      <ReactApexChart
        options={options}
        series={series}
        type="candlestick"
        height={400}
      />
    </div>
  )
}

export default CandlestickChart
