import { useEffect, useState, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import { Stock, WebSocketMessage, SignalAlert } from '../types'

export function useStockWebSocket() {
  const [stocks, setStocks] = useState<Stock[]>([])
  const [alerts, setAlerts] = useState<SignalAlert[]>([])
  const [isConnected, setIsConnected] = useState(false)
  const [client, setClient] = useState<Client | null>(null)

  useEffect(() => {
    try {
      const stompClient = new Client({
        brokerURL: `ws://${window.location.hostname}:8080/ws`,
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,

        onConnect: () => {
          console.log('WebSocket Connected')
          setIsConnected(true)

          stompClient.subscribe('/topic/stock-updates', (message) => {
            const data: WebSocketMessage = JSON.parse(message.body)
            if (data.type === 'STOCK_UPDATE') {
              const stockData = data.data as Stock
              setStocks((prev) => {
                const index = prev.findIndex((s) => s.symbol === stockData.symbol)
                if (index >= 0) {
                  const newStocks = [...prev]
                  newStocks[index] = stockData
                  return newStocks
                }
                return [...prev, stockData]
              })
            }
          })

          stompClient.subscribe('/topic/signals', (message) => {
            const data: WebSocketMessage = JSON.parse(message.body)
            if (data.type === 'SIGNAL_ALERT') {
              const alert = data.data as SignalAlert
              setAlerts((prev) => [alert, ...prev].slice(0, 50))
            }
          })
        },

        onDisconnect: () => {
          console.log('WebSocket Disconnected')
          setIsConnected(false)
        },

        onStompError: (frame) => {
          console.error('STOMP Error:', frame)
          setIsConnected(false)
        },

        onWebSocketError: (event) => {
          console.error('WebSocket Error:', event)
          setIsConnected(false)
        }
      })

      stompClient.activate()
      setClient(stompClient)

      return () => {
        if (stompClient.active) {
          stompClient.deactivate()
        }
      }
    } catch (err) {
      console.error('WebSocket initialization failed:', err)
    }
  }, [])

  const subscribeToStock = useCallback((symbol: string) => {
    if (client && client.active) {
      client.subscribe(`/topic/stock/${symbol}`, (message) => {
        const data = JSON.parse(message.body)
        console.log(`Received update for ${symbol}:`, data)
      })
    }
  }, [client])

  return {
    stocks,
    alerts,
    isConnected,
    subscribeToStock
  }
}
