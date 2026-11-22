import type { Priority, Status } from './api'

export const STATUS_LABEL: Record<Status, string> = {
  NEW: 'New', CLASSIFIED: 'Awaiting assignment', ASSIGNED: 'Assigned', ACKNOWLEDGED: 'Acknowledged',
  IN_PROGRESS: 'In progress', AWAITING_INFO: 'Waiting on reporter', RESOLVED: 'Resolved', VERIFIED: 'Verified',
  CLOSED: 'Closed', CANCELLED: 'Cancelled',
}

export const PRIORITY_LABEL: Record<Priority, string> = { P1: 'Critical', P2: 'High', P3: 'Medium', P4: 'Low' }

export const LIFECYCLE: Status[] = ['NEW', 'CLASSIFIED', 'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'VERIFIED', 'CLOSED']

const dtf = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
const tf = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' })

export const fmtDateTime = (iso?: string) => (iso ? dtf.format(new Date(iso)) : '—')
export const fmtTime = (iso?: string) => (iso ? tf.format(new Date(iso)) : '—')

export function ago(iso: string, now = Date.now()): string {
  const s = Math.round((now - new Date(iso).getTime()) / 1000)
  if (s < 45) return 'just now'
  const m = Math.round(s / 60)
  if (m < 60) return `${m}m ago`
  const h = Math.round(m / 60)
  if (h < 24) return `${h}h ago`
  const d = Math.round(h / 24)
  return `${d}d ago`
}

/** Signed countdown: "12:04", "3h 20m", "-0:45" (overdue). */
export function countdown(ms: number): string {
  const neg = ms < 0
  let s = Math.floor(Math.abs(ms) / 1000)
  const d = Math.floor(s / 86400); s -= d * 86400
  const h = Math.floor(s / 3600); s -= h * 3600
  const m = Math.floor(s / 60); s -= m * 60
  let out: string
  if (d > 0) out = `${d}d ${h}h`
  else if (h > 0) out = `${h}h ${String(m).padStart(2, '0')}m`
  else out = `${m}:${String(s).padStart(2, '0')}`
  return neg ? `−${out}` : out
}

export function minutes(m: number): string {
  if (m < 60) return `${m} min`
  if (m % 1440 === 0) return `${m / 1440} d`
  if (m % 60 === 0) return `${m / 60} h`
  return `${Math.floor(m / 60)} h ${m % 60} min`
}

export const titleCase = (s: string) => s.toLowerCase().replace(/_/g, ' ').replace(/^\w/, c => c.toUpperCase()).replace(/\bit\b/i, 'IT')

/** Remaining time for display: "1h 05m" (or "1h 05m left"), and "2d 3h late" once overdue, never a minus sign. */
export function timeLeft(ms: number, withLeft = false): string {
  return ms < 0 ? `${countdown(-ms)} late` : withLeft ? `${countdown(ms)} left` : countdown(ms)
}
