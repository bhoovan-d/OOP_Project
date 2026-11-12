import { useEffect, useRef, type ReactNode } from 'react'
import { ChevronLeft, ChevronRight, Loader2, X } from 'lucide-react'
import type { Priority, SlaStatus, Status } from '../lib/api'
import { PRIORITY_LABEL, STATUS_LABEL, countdown } from '../lib/format'
import { useNow } from '../lib/live'

const cx = (...c: (string | false | null | undefined)[]) => c.filter(Boolean).join(' ')
export { cx }

/* ---------------- Triage tag: the priority, worn like an ER tag ---------------- */

const TAG: Record<Priority, string> = {
  P1: 'bg-p1 text-white', P2: 'bg-p2 text-white', P3: 'bg-p3 text-ink', P4: 'bg-p4 text-white',
}

export function TriageTag({ priority, size = 'md', withLabel }: { priority?: Priority; size?: 'sm' | 'md' | 'lg'; withLabel?: boolean }) {
  if (!priority) return <span className="mono text-[11px] text-ink-3 px-1.5">—</span>
  const dims = size === 'lg' ? 'h-8 pl-3 pr-3 text-[14px]' : size === 'sm' ? 'h-5 pl-2 pr-1.5 text-[10.5px]' : 'h-6 pl-2.5 pr-2 text-[11.5px]'
  return (
    <span
      className={cx('relative inline-flex items-center gap-1.5 font-mono font-semibold rounded-[3px] rounded-l-[9px] select-none', TAG[priority], dims)}
      title={`${priority} · ${PRIORITY_LABEL[priority]}`}
    >
      <span aria-hidden className="absolute left-[4px] top-1/2 -translate-y-1/2 size-[5px] rounded-full bg-white/85 ring-1 ring-black/10" />
      <span className="ml-[3px]">{priority}</span>
      {withLabel && <span className="font-sans font-bold tracking-normal">{PRIORITY_LABEL[priority]}</span>}
    </span>
  )
}

/* ---------------- SLA strip: remaining time drawn as length, plus a live countdown ---------------- */

export function SlaStrip({ start, due, status, compact, paused }: { start: string; due?: string; status: SlaStatus; compact?: boolean; paused?: boolean }) {
  const now = useNow()
  if (!due) return <span className="text-ink-3 text-[12px]">No SLA</span>
  const s = new Date(start).getTime(), d = new Date(due).getTime()
  const left = d - now
  const frac = Math.max(0, Math.min(1, left / Math.max(1, d - s)))
  const closed = status === 'MET'
  const tone = paused || status === 'PAUSED' ? 'paused' : closed ? 'met' : left < 0 || status === 'BREACHED' ? 'breached' : frac < 0.25 || status === 'AT_RISK' ? 'risk' : 'ok'
  const bar = { ok: 'bg-ink-2', risk: 'bg-p3', breached: 'bg-p1', met: 'bg-ok', paused: 'bg-ink-3/50' }[tone]
  const text = { ok: 'text-ink-2', risk: 'text-warn', breached: 'text-bad', met: 'text-ok', paused: 'text-ink-3' }[tone]
  const label = closed ? 'Met' : tone === 'paused' ? 'Paused' : countdown(left)
  return (
    <span className={cx('inline-flex items-center gap-2', compact ? 'w-[132px]' : 'w-full')} title={`Due ${new Date(due).toLocaleString()}`}>
      <span className={cx('relative h-[6px] rounded-full bg-sunken overflow-hidden flex-1', tone === 'breached' && 'bg-p1-soft')}>
        <span className={cx('absolute inset-y-0 left-0 rounded-full transition-[width] duration-1000 ease-linear', bar)}
              style={{ width: `${tone === 'breached' ? 100 : tone === 'met' ? 100 : frac * 100}%` }} />
      </span>
      <span className={cx('mono text-[12px] font-semibold min-w-[58px] text-right', text)}>{label}</span>
    </span>
  )
}

/* ---------------- Status ---------------- */

const STATUS_TONE: Record<Status, string> = {
  NEW: 'bg-sunken text-ink-2 border-line-strong',
  CLASSIFIED: 'bg-p2-soft text-[#9a3f06] border-p2/30',
  ASSIGNED: 'bg-action-soft text-action border-action/25',
  ACKNOWLEDGED: 'bg-action-soft text-action border-action/25',
  IN_PROGRESS: 'bg-[#e7f0ff] text-[#1a3f9e] border-[#1a3f9e]/25',
  AWAITING_INFO: 'bg-[#f1ecfd] text-[#5b36b0] border-[#5b36b0]/25',
  RESOLVED: 'bg-p4-soft text-[#1f6b30] border-p4/30',
  VERIFIED: 'bg-p4-soft text-[#1f6b30] border-p4/30',
  CLOSED: 'bg-sunken text-ink-3 border-line',
  CANCELLED: 'bg-sunken text-ink-3 border-line line-through',
}

export function StatusBadge({ status }: { status: Status }) {
  return <span className={cx('chip', STATUS_TONE[status])}>{STATUS_LABEL[status]}</span>
}

/* ---------------- Layout bits ---------------- */

export function PageHeader({ title, sub, actions }: { title: string; sub?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4 mb-6">
      <div className="min-w-0">
        <h1 className="text-[22px] leading-tight font-extrabold text-ink">{title}</h1>
        {sub && <p className="text-ink-3 mt-1 text-[13.5px]">{sub}</p>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  )
}

export function Empty({ icon, title, children }: { icon?: ReactNode; title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center text-center py-14 px-6">
      {icon && <div className="text-ink-3 mb-3">{icon}</div>}
      <p className="font-bold text-ink">{title}</p>
      {children && <div className="text-ink-3 text-[13px] mt-1 max-w-[46ch]">{children}</div>}
    </div>
  )
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cx('animate-pulse rounded bg-sunken', className)} />
}

export function SkeletonRows({ rows = 6, cols = 5 }: { rows?: number; cols?: number }) {
  return (
    <div className="p-4 space-y-3">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="flex gap-4">{Array.from({ length: cols }).map((_, j) => <Skeleton key={j} className="h-5 flex-1" />)}</div>
      ))}
    </div>
  )
}

export function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null
  return <div role="alert" className="rounded-md bg-p1-soft text-bad text-[13px] px-3 py-2 border border-bad/20">{(error as Error).message}</div>
}

export function Field({ label, error, hint, children }: { label: string; error?: string; hint?: ReactNode; children: ReactNode }) {
  return (
    <label className="block">
      <span className="field-label">{label}</span>
      {children}
      {error ? <span className="block text-bad text-[12px] mt-1">{error}</span> : hint ? <span className="block text-ink-3 text-[12px] mt-1">{hint}</span> : null}
    </label>
  )
}

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cx('animate-spin', className ?? 'size-4')} />
}

export function Pager({ page, totalPages, total, onPage }: { page: number; totalPages: number; total: number; onPage: (p: number) => void }) {
  return (
    <div className="flex items-center justify-between px-4 h-12 text-[12.5px] text-ink-3">
      <span className="tnum">{total.toLocaleString()} result{total === 1 ? '' : 's'}</span>
      <div className="flex items-center gap-1">
        <button className="btn-ghost btn-sm" disabled={page <= 0} onClick={() => onPage(page - 1)} aria-label="Previous page"><ChevronLeft className="size-4" /></button>
        <span className="tnum px-2">Page {totalPages === 0 ? 0 : page + 1} of {totalPages}</span>
        <button className="btn-ghost btn-sm" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)} aria-label="Next page"><ChevronRight className="size-4" /></button>
      </div>
    </div>
  )
}

/** Native <dialog>: real focus trapping, Esc to close, top layer (never clipped). */
export function Dialog({ open, onClose, title, children, wide }: { open: boolean; onClose: () => void; title: string; children: ReactNode; wide?: boolean }) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const d = ref.current
    if (!d) return
    if (open && !d.open) d.showModal()
    if (!open && d.open) d.close()
  }, [open])
  return (
    <dialog ref={ref} onClose={onClose} onCancel={onClose}
            className={cx('m-auto rounded-xl border border-line p-0 shadow-[0_24px_60px_-12px_rgba(15,35,65,.35)] backdrop:bg-rail/40 w-[calc(100%-32px)]', wide ? 'max-w-[720px]' : 'max-w-[480px]')}>
      {open && (
        <div>
          <div className="flex items-center justify-between px-5 h-14 border-b border-line">
            <h2 className="font-bold text-[15px]">{title}</h2>
            <button className="btn-ghost btn-sm" onClick={onClose} aria-label="Close"><X className="size-4" /></button>
          </div>
          <div className="p-5">{children}</div>
        </div>
      )}
    </dialog>
  )
}

/** Capacity bar: fills toward the technician's limit; never visually overflows, turns red at the limit. */
export function CapacityBar({ value, max }: { value: number; max: number }) {
  const pct = Math.min(1, value / Math.max(1, max))
  const tone = pct >= 1 ? 'bg-p1' : pct >= 0.8 ? 'bg-p3' : 'bg-ink-2'
  return (
    <span className="inline-flex items-center gap-2 w-full">
      <span className="flex gap-[3px] flex-1" aria-label={`${value} of ${max} slots used`}>
        {Array.from({ length: max }).map((_, i) => (
          <span key={i} className={cx('h-[8px] flex-1 rounded-[2px]', i < value ? tone : 'bg-sunken border border-line')} />
        ))}
      </span>
      <span className="mono text-[12px] text-ink-2 w-9 text-right">{value}/{max}</span>
    </span>
  )
}
