import { Link } from 'react-router-dom'
import { ArrowDown, ArrowUp, Copy, Siren } from 'lucide-react'
import type { Summary } from '../lib/api'
import { ago } from '../lib/format'
import { useLive, useNow } from '../lib/live'
import { SlaStrip, StatusBadge, PriorityTag, cx } from './ui'

export type SortState = { sort: string; dir: 'asc' | 'desc' }

type Col = 'reporter' | 'assignee' | 'service' | 'category'

/** The triage board: tag first, then identity, then time-left as a physical strip. */
export function IncidentTable({ rows, cols = ['service', 'assignee'], sort, onSort }:
  { rows: Summary[]; cols?: Col[]; sort?: SortState; onSort?: (s: SortState) => void }) {
  const now = useNow()
  const { lastIncidentId } = useLive()
  const header = (key: string, label: string, cls = '') => {
    const active = sort?.sort === key
    if (!onSort) return <th className={cx('th', cls)}>{label}</th>
    return (
      <th className={cx('th', cls)} aria-sort={active ? (sort!.dir === 'asc' ? 'ascending' : 'descending') : 'none'}>
        <button className="inline-flex items-center gap-1 uppercase hover:text-ink" onClick={() => onSort({ sort: key, dir: active && sort!.dir === 'desc' ? 'asc' : 'desc' })}>
          {label}{active && (sort!.dir === 'asc' ? <ArrowUp className="size-3" /> : <ArrowDown className="size-3" />)}
        </button>
      </th>
    )
  }
  return (
    <>
    <ul className="md:hidden divide-y divide-line" aria-label="Incidents">
      {rows.map(r => {
        const open = !['RESOLVED', 'VERIFIED', 'CLOSED', 'CANCELLED'].includes(r.status)
        return (
          <li key={r.id} className={cx(lastIncidentId === r.id && 'row-flash')}>
            <Link to={`/incidents/${r.id}`} className="block px-4 py-3 hover:bg-sunken">
              <span className="flex items-center gap-2 flex-wrap">
                <PriorityTag priority={r.priority} />
                <span className="mono text-[17px] font-bold leading-none">{r.number}</span>
                <StatusBadge status={r.status} />
                {r.escalationLevel !== 'NONE' && open && <span className="chip h-5 bg-sunken text-ink-2 border-line-strong"><Siren className="size-3" />Escalated</span>}
              </span>
              <span className="block font-semibold text-ink mt-1.5">{r.title}</span>
              <span className="block text-[13px] text-ink-3 truncate">
                {[cols.includes('service') && r.service?.name, cols.includes('category') && r.category?.name, cols.includes('reporter') && r.reporter?.name,
                  cols.includes('assignee') && (r.assignee?.name ?? 'Unassigned')].filter(Boolean).join(' · ')}
              </span>
              <span className="block mt-2">
                {open
                  ? <SlaStrip start={r.createdAt} due={r.resolutionDueAt} status={r.slaStatus} paused={r.status === 'AWAITING_INFO'} />
                  : <span className={cx('text-[13px] font-semibold', r.breached ? 'text-bad' : 'text-ok')}>{r.status === 'CANCELLED' ? '—' : r.breached ? 'SLA missed' : 'Within SLA'}</span>}
              </span>
            </Link>
          </li>
        )
      })}
    </ul>
    <div className="hidden md:block overflow-x-auto">
      <table className="w-full border-collapse min-w-[860px]">
        <thead>
          <tr>
            {header('priority', 'Pri', 'w-[64px]')}
            <th className="th">Incident</th>
            {header('status', 'Status', 'w-[170px]')}
            {cols.includes('service') && <th className="th w-[170px]">Service</th>}
            {cols.includes('category') && <th className="th w-[130px]">Category</th>}
            {cols.includes('reporter') && <th className="th w-[150px]">Reporter</th>}
            {cols.includes('assignee') && <th className="th w-[150px]">Technician</th>}
            {header('resolutionDueAt', 'SLA remaining', 'w-[244px]')}
            {header('createdAt', 'Opened', 'w-[96px]')}
          </tr>
        </thead>
        <tbody>
          {rows.map(r => {
            const open = !['RESOLVED', 'VERIFIED', 'CLOSED', 'CANCELLED'].includes(r.status)
            return (
              <tr key={r.id} className={cx('group hover:bg-sunken/60', lastIncidentId === r.id && 'row-flash')}>
                <td className="td"><PriorityTag priority={r.priority} /></td>
                <td className="td min-w-0">
                  <Link to={`/incidents/${r.id}`} className="block min-w-0 focus-visible:outline-offset-4">
                    <span className="flex items-center gap-2">
                      <span className="mono text-[17px] font-bold leading-none text-ink group-hover:text-action">{r.number}</span>
                      {r.escalationLevel !== 'NONE' && open && (
                        <span className="chip h-5 bg-sunken text-ink-2 border-line-strong" title={`Escalated to ${r.escalationLevel.replace('_', ' ').toLowerCase()}`}><Siren className="size-3" />Escalated</span>
                      )}
                      {r.duplicateOfNumber && <span className="chip h-5 bg-sunken text-ink-3 border-line" title={`Linked to ${r.duplicateOfNumber}`}><Copy className="size-3" />{r.duplicateOfNumber}</span>}
                    </span>
                    <span className="block font-semibold text-ink truncate max-w-[52ch] mt-0.5 group-hover:underline">{r.title}</span>
                  </Link>
                </td>
                <td className="td"><StatusBadge status={r.status} /></td>
                {cols.includes('service') && <td className="td text-ink-2 text-[13.5px] truncate max-w-[170px]">{r.service?.name}</td>}
                {cols.includes('category') && <td className="td text-ink-2 text-[13.5px]">{r.category?.name ?? '—'}</td>}
                {cols.includes('reporter') && <td className="td text-ink-2 text-[13.5px] truncate">{r.reporter?.name}</td>}
                {cols.includes('assignee') && <td className="td text-[13.5px] truncate">{r.assignee?.name ?? <span className="text-warn font-semibold">Unassigned</span>}</td>}
                <td className="td">
                  {open
                    ? <SlaStrip compact start={r.createdAt} due={r.resolutionDueAt} status={r.slaStatus} paused={r.status === 'AWAITING_INFO'} />
                    : <span className={cx('text-[13px] font-semibold', r.breached ? 'text-bad' : 'text-ok')}>{r.status === 'CANCELLED' ? '—' : r.breached ? 'SLA missed' : 'Within SLA'}</span>}
                </td>
                <td className="td text-ink-3 text-[13px] tnum whitespace-nowrap">{ago(r.createdAt, now)}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
    </>
  )
}
