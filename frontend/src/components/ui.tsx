import { useEffect, useRef, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { ChevronLeft, ChevronRight, Loader2, X } from 'lucide-react'
import type { Priority, SlaStatus, Status } from '../lib/api'
import { PRIORITY_LABEL, STATUS_LABEL, timeLeft } from '../lib/format'
import { useNow } from '../lib/live'

const cx = (...c: (string | false | null | undefined)[]) => c.filter(Boolean).join(' ')
export { cx }

/* ---------------- Priority tag: a coloured plate with the P-number painted on ---------------- */

const TAG: Record<Priority, string> = {
  P1: 'bg-p1 text-white', P2: 'bg-p2 text-white', P3: 'bg-p3 text-ink', P4: 'bg-p4 text-white',
}

export function PriorityTag({ priority, size = 'md', withLabel }: { priority?: Priority; size?: 'sm' | 'md' | 'lg'; withLabel?: boolean }) {
  if (!priority) return <span className="mono text-[13px] text-ink-3 px-1.5">—</span>
  const dims = size === 'lg' ? 'h-8 px-2.5 text-[18px]' : size === 'sm' ? 'h-5 px-1.5 text-[13px]' : 'h-6 px-2 text-[15px]'
  return (
    <span className={cx('inline-flex items-center gap-1.5 font-display font-extrabold leading-none rounded-[2px] select-none tabular-nums', TAG[priority], dims)}
          title={`${priority} · ${PRIORITY_LABEL[priority]}`}>
      <span className="pt-px">{priority}</span>
      {withLabel && <span className="font-bold tracking-[0.02em] uppercase">{PRIORITY_LABEL[priority]}</span>}
    </span>
  )
}

/* ---------------- SLA as ball slots: the window split into 12, filled as time is used ---------------- */

const SLOTS = 12

export function SlaStrip({ start, due, status, compact, paused }: { start: string; due?: string; status: SlaStatus; compact?: boolean; paused?: boolean }) {
  const now = useNow()
  if (!due) return <span className="text-ink-3 text-[13px]">No SLA</span>
  const s = new Date(start).getTime(), d = new Date(due).getTime()
  const left = d - now
  const frac = Math.max(0, Math.min(1, left / Math.max(1, d - s)))
  const closed = status === 'MET'
  const tone = paused || status === 'PAUSED' ? 'paused' : closed ? 'met' : left < 0 || status === 'BREACHED' ? 'breached' : frac < 0.25 || status === 'AT_RISK' ? 'risk' : 'ok'
  const used = tone === 'breached' ? SLOTS : tone === 'met' ? 0 : Math.min(SLOTS, Math.ceil((1 - frac) * SLOTS))
  const ball = { ok: 'border-board-2 bg-board-2', risk: 'border-p3 bg-p3', breached: 'border-p1 bg-p1', met: 'border-ok bg-ok', paused: 'border-ink-3 bg-ink-3' }[tone]
  const ring = { ok: 'border-board-2/55', risk: 'border-p3', breached: 'border-p1', met: 'border-ok/60', paused: 'border-ink-3/50 border-dashed' }[tone]
  const text = { ok: 'text-ink', risk: 'text-warn', breached: 'text-bad', met: 'text-ok', paused: 'text-ink-3' }[tone]
  const label = closed ? 'Met' : tone === 'paused' ? 'Paused' : timeLeft(left)
  return (
    <span className={cx('inline-flex items-center gap-2.5', compact ? 'w-[232px]' : 'w-full')} title={`Due ${new Date(due).toLocaleString()} · ${used} of ${SLOTS} slots used`}>
      <span className="flex items-center gap-[3px] shrink-0" aria-hidden>
        {Array.from({ length: SLOTS }).map((_, i) => (
          <span key={i} className={cx('size-[8px] rounded-full border-[1.5px]', i === 6 && 'ml-[4px]', i < used ? ball : ring)} />
        ))}
      </span>
      <span className={cx('mono text-[16px] font-bold leading-none min-w-[54px]', text)}>{label}</span>
    </span>
  )
}

/* ---------------- Plates and the board ---------------- */

/** A hung white number plate. The value flips in two frames when it changes. */
export function Plate({ value, unit, tone, reversed, className }: { value?: string | number; unit?: string; tone?: string; reversed?: boolean; className?: string }) {
  return (
    <span className={cx('plate flex items-end justify-center px-3 pt-4 pb-1.5 min-h-[70px]', reversed && 'plate-reversed', className)}>
      {value === undefined
        ? <span className="h-10 w-12 bg-line/70 rounded-[2px] animate-pulse" />
        : <span key={String(value)} className={cx('plate-flip font-display font-extrabold text-[52px] leading-[0.9] tabular-nums', tone)}>
            {value}{unit && <span className="text-[26px] font-bold ml-0.5">{unit}</span>}
          </span>}
    </span>
  )
}

/** The green board the plates hang on. */
export function Board({ children, className, label }: { children: ReactNode; className?: string; label: string }) {
  return <section aria-label={label} className={cx('bg-board rounded-[6px] p-3.5 sm:p-4 grid gap-3', className)}>{children}</section>
}

/** One hung count: plate above, chalk label below. Links when `to` is given. */
export function BoardCount({ label, value, unit, tone, to }: { label: string; value?: string | number; unit?: string; tone?: string; to?: string }) {
  const body = (
    <>
      <Plate value={value} unit={unit} tone={tone} />
      <span className="block text-center text-[11.5px] font-semibold uppercase tracking-[0.12em] text-chalk mt-2">{label}</span>
    </>
  )
  return to ? <Link to={to} className="block rounded-[3px] hover:[&_.plate]:bg-white">{body}</Link> : <div>{body}</div>
}

/** The single reversed plate on a screen: the one thing that needs attention now. */
export function NowPlate({ head, detail, to }: { head: ReactNode; detail?: ReactNode; to?: string }) {
  const body = (
    <span className="plate plate-reversed block h-full px-4 pt-5 pb-3">
      <span className="flex flex-wrap items-center gap-x-2.5 gap-y-1 font-display font-extrabold text-[30px] sm:text-[34px] leading-none tabular-nums">{head}</span>
      {detail && <span className="block text-[13.5px] text-[#cfd9d3] mt-2.5 leading-snug">{detail}</span>}
    </span>
  )
  return to ? <Link to={to} className="block h-full">{body}</Link> : <div className="h-full">{body}</div>
}

/** The coloured lead word inside a NowPlate detail line: red danger, amber risk, green clear, violet waiting. */
export function NowLead({ tone, children }: { tone: 'danger' | 'risk' | 'clear' | 'waiting'; children: ReactNode }) {
  const c = { danger: 'text-[#ff9c92]', risk: 'text-[#ffd36b]', clear: 'text-[#8fd3ad]', waiting: 'text-[#cbb9f2]' }[tone]
  return <b className={cx('font-semibold', c)}>{children}</b>
}

/* ---------------- Status ---------------- */

const STATUS_TONE: Record<Status, string> = {
  NEW: 'bg-sunken text-ink-2 border-line-strong',
  CLASSIFIED: 'bg-sunken text-ink-2 border-line-strong',
  ASSIGNED: 'bg-action-soft text-action border-action/25',
  ACKNOWLEDGED: 'bg-action-soft text-action border-action/25',
  IN_PROGRESS: 'bg-board-2 text-white border-board-2',
  AWAITING_INFO: 'bg-[#efeaf7] text-[#553a8f] border-[#553a8f]/25',
  RESOLVED: 'bg-[#e3f1e8] text-ok border-ok/30',
  VERIFIED: 'bg-[#e3f1e8] text-ok border-ok/30',
  CLOSED: 'bg-sunken text-ink-3 border-line',
  CANCELLED: 'bg-sunken text-ink-3 border-line line-through',
}

export function StatusBadge({ status }: { status: Status }) {
  return <span className={cx('chip', STATUS_TONE[status])}>{STATUS_LABEL[status]}</span>
}

/* ---------------- Layout bits ---------------- */

export function PageHeader({ title, sub, actions }: { title: string; sub?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4 mb-5">
      <div className="min-w-0">
        <h1 className="font-display text-[30px] leading-[1.05] font-bold text-ink tracking-[0.005em]">{title}</h1>
        {sub && <p className="text-ink-3 mt-1 text-[14px]">{sub}</p>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  )
}

export function Empty({ icon, title, children }: { icon?: ReactNode; title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center text-center py-14 px-6">
      {icon && <div className="text-ink-3 mb-3">{icon}</div>}
      <p className="font-display text-[20px] font-bold text-ink">{title}</p>
      {children && <div className="text-ink-3 text-[14px] mt-1 max-w-[46ch]">{children}</div>}
    </div>
  )
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cx('animate-pulse rounded-[2px] bg-sunken', className)} />
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
  return <div role="alert" className="rounded-[4px] bg-p1-soft text-bad text-[13.5px] px-3 py-2 border border-bad/25">{(error as Error).message}</div>
}

export function Field({ label, error, hint, children }: { label: string; error?: string; hint?: ReactNode; children: ReactNode }) {
  return (
    <label className="block">
      <span className="field-label">{label}</span>
      {children}
      {error ? <span className="block text-bad text-[12.5px] mt-1">{error}</span> : hint ? <span className="block text-ink-3 text-[12.5px] mt-1">{hint}</span> : null}
    </label>
  )
}

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cx('animate-spin', className ?? 'size-4')} />
}

export function Pager({ page, totalPages, total, onPage }: { page: number; totalPages: number; total: number; onPage: (p: number) => void }) {
  return (
    <div className="flex items-center justify-between px-4 h-12 text-[13px] text-ink-3">
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
            className={cx('m-auto rounded-[6px] border border-line p-0 shadow-[0_24px_60px_-12px_rgba(8,30,22,.45)] backdrop:bg-board/50 w-[calc(100%-32px)]', wide ? 'max-w-[720px]' : 'max-w-[480px]')}>
      {open && (
        <div>
          <div className="flex items-center justify-between px-5 h-14 border-b border-line">
            <h2 className="font-display font-bold text-[20px]">{title}</h2>
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
  const tone = pct >= 1 ? 'bg-p1' : pct >= 0.8 ? 'bg-p3' : 'bg-board-2'
  return (
    <span className="inline-flex items-center gap-2 w-full">
      <span className="flex gap-[3px] flex-1 min-w-[72px]" aria-label={`${value} of ${max} slots used`}>
        {Array.from({ length: max }).map((_, i) => (
          <span key={i} className={cx('h-[10px] flex-1 min-w-[8px] rounded-[1px]', i < value ? tone : 'bg-sunken border border-line-strong/70')} />
        ))}
      </span>
      <span className="mono text-[15px] font-bold text-ink w-10 text-right">{value}/{max}</span>
    </span>
  )
}
