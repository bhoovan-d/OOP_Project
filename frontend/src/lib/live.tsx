import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { tokenStore } from './api'
import { useAuth } from './auth'

/** One shared 1-second clock so every SLA countdown ticks together without one interval per row. */
const NowCtx = createContext(Date.now())
export const useNow = () => useContext(NowCtx)

const LiveCtx = createContext<{ connected: boolean; lastIncidentId?: number }>({ connected: false })
export const useLive = () => useContext(LiveCtx)

export function LiveProvider({ children }: { children: ReactNode }) {
  const [now, setNow] = useState(Date.now())
  const [connected, setConnected] = useState(false)
  const [lastIncidentId, setLast] = useState<number>()
  const { user } = useAuth()
  const qc = useQueryClient()

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(t)
  }, [])

  // Server-Sent Events: incident changes refresh the relevant queries; notifications pop a toast.
  useEffect(() => {
    if (!user) return
    const token = tokenStore.get()
    if (!token) return
    let es: EventSource | null = null
    let retry: ReturnType<typeof setTimeout>
    const connect = () => {
      es = new EventSource(`/api/stream?access_token=${encodeURIComponent(token)}`)
      es.addEventListener('hello', () => setConnected(true))
      es.addEventListener('incident', e => {
        const d = JSON.parse((e as MessageEvent).data)
        setLast(d.incidentId)
        qc.invalidateQueries({ queryKey: ['incidents'] })
        qc.invalidateQueries({ queryKey: ['incident', d.incidentId] })
        qc.invalidateQueries({ queryKey: ['dashboard'] })
        qc.invalidateQueries({ queryKey: ['mystats'] })
      })
      es.addEventListener('notification', e => {
        const n = JSON.parse((e as MessageEvent).data)
        qc.invalidateQueries({ queryKey: ['notifications'] })
        const urgent = /SLA|Escalat|Major/i.test(n.title)
        ;(urgent ? toast.warning : toast)(n.title, { description: n.message })
      })
      es.onerror = () => {
        setConnected(false)
        es?.close()
        retry = setTimeout(connect, 5000)
      }
    }
    connect()
    return () => { es?.close(); clearTimeout(retry); setConnected(false) }
  }, [user, qc])

  return (
    <NowCtx.Provider value={now}>
      <LiveCtx.Provider value={{ connected, lastIncidentId }}>{children}</LiveCtx.Provider>
    </NowCtx.Provider>
  )
}
