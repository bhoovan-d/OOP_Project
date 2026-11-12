import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { CircleAlert, CircleCheck, Copy, OctagonAlert, TrendingUp } from 'lucide-react'
import { api, type Dashboard as D, type Priority } from '../lib/api'
import { countdown, minutes, PRIORITY_LABEL } from '../lib/format'
import { useNow } from '../lib/live'
import { CapacityBar, Empty, ErrorNote, PageHeader, Skeleton, TriageTag, cx } from '../components/ui'

const SERIES = { created: '#1f4fd1', resolved: '#0e9384' } // validated categorical pair (CVD ΔE 22)

export default function Dashboard() {
  const q = useQuery({ queryKey: ['dashboard'], queryFn: () => api<D>('/reports/dashboard?days=14'), refetchInterval: 30_000 })
  if (q.error) return <ErrorNote error={q.error} />
  const d = q.data

  return (
    <>
      <PageHeader title="Operations" sub="Live view of incidents, SLA health and team capacity. Updates as events happen." />
      <Kpis d={d} />
      <div className="grid xl:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)] gap-5 mt-5">
        <ServiceBoard d={d} />
        <RiskList d={d} />
      </div>
      <div className="grid xl:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)] gap-5 mt-5">
        <TrendChart d={d} />
        <PriorityMix d={d} />
      </div>
      <div className="grid xl:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)] gap-5 mt-5">
        <Workload d={d} />
        <div className="space-y-5">
          <Clusters d={d} />
          <Mttr d={d} />
        </div>
      </div>
      <div className="grid lg:grid-cols-2 gap-5 mt-5">
        <CategoryChart d={d} />
        <ServiceTable d={d} />
      </div>
    </>
  )
}

function Kpis({ d }: { d?: D }) {
  const o = d?.overview
  const cells: { label: string; value?: string | number; tone?: string; to?: string; note?: string }[] = [
    { label: 'Open incidents', value: o?.open, to: '/incidents?open=true' },
    { label: 'Critical open', value: o?.criticalOpen, tone: o && o.criticalOpen > 0 ? 'text-bad' : undefined, to: '/incidents?open=true&priority=P1' },
    { label: 'At risk now', value: o?.atRisk, tone: o && o.atRisk > 0 ? 'text-warn' : undefined, to: '/incidents?slaStatus=AT_RISK' },
    { label: 'Unassigned', value: o?.unassigned, tone: o && o.unassigned > 0 ? 'text-p2' : undefined, to: '/incidents?unassigned=true&open=true' },
    { label: 'SLA compliance', value: o ? `${o.slaCompliancePct}%` : undefined, note: 'resolved in last 14 days' },
    { label: 'Mean time to resolve', value: o ? (o.mttrMinutes < 60 ? `${Math.round(o.mttrMinutes)} min` : `${(o.mttrMinutes / 60).toFixed(1)} h`) : undefined, note: 'last 14 days' },
    { label: 'SLA violations', value: o?.breached, to: '/incidents?breached=true', note: 'all time' },
    { label: 'Satisfaction', value: o ? `${o.avgRating} / 5` : undefined, note: o ? `${o.ratings} ratings` : undefined },
  ]
  return (
    <section className="panel grid grid-cols-2 sm:grid-cols-4 xl:grid-cols-8 divide-x divide-y xl:divide-y-0 divide-line overflow-hidden" aria-label="Key figures">
      {cells.map(c => {
        const body = (
          <>
            <div className="text-[12px] font-semibold text-ink-3 truncate">{c.label}</div>
            {c.value === undefined ? <Skeleton className="h-7 w-14 mt-1" /> : <div className={cx('mono text-[22px] font-semibold mt-0.5 leading-tight', c.tone ?? 'text-ink')}>{c.value}</div>}
            {c.note && <div className="text-[11.5px] text-ink-3 truncate">{c.note}</div>}
          </>
        )
        return c.to
          ? <Link key={c.label} to={c.to} className="px-4 py-3.5 hover:bg-sunken/60 transition-colors">{body}</Link>
          : <div key={c.label} className="px-4 py-3.5">{body}</div>
      })}
    </section>
  )
}

/** Signature: services on the board, each wearing its health like a triage tag. */
function ServiceBoard({ d }: { d?: D }) {
  const icon = { OUTAGE: <OctagonAlert className="size-4" />, DEGRADED: <CircleAlert className="size-4" />, HEALTHY: <CircleCheck className="size-4" /> }
  const tone = { OUTAGE: 'bg-p1 text-white', DEGRADED: 'bg-p3 text-ink', HEALTHY: 'bg-p4-soft text-[#1f6b30]' }
  const word = { OUTAGE: 'Outage', DEGRADED: 'Degraded', HEALTHY: 'Healthy' }
  return (
    <section className="panel">
      <div className="panel-head"><span className="panel-title">Service health</span><span className="text-[12px] text-ink-3">from open incidents, last 60 min</span></div>
      {!d ? <div className="p-4 space-y-2">{Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-9" />)}</div> : (
        <ul className="grid sm:grid-cols-2">
          {d.serviceHealth.map(s => (
            <li key={s.serviceId} className="border-b border-line sm:odd:border-r">
              <Link to={`/incidents?serviceId=${s.serviceId}&open=true`} className="flex items-center gap-3 px-4 py-2.5 hover:bg-sunken/60">
                <span className={cx('inline-flex items-center gap-1.5 h-6 pl-1.5 pr-2 rounded-[3px] rounded-l-[9px] text-[11.5px] font-bold w-[96px]', tone[s.health])}>{icon[s.health]}{word[s.health]}</span>
                <span className="min-w-0 flex-1">
                  <span className="block text-[13px] font-semibold truncate">{s.service}</span>
                  <span className="block text-[11.5px] text-ink-3">{s.criticality.replace('_', ' ').toLowerCase()}</span>
                </span>
                <span className="mono text-[12.5px] text-ink-2 text-right">{s.open} open{s.recent > 0 && <span className="block text-[11px] text-ink-3">{s.recent} new</span>}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function RiskList({ d }: { d?: D }) {
  const now = useNow()
  return (
    <section className="panel">
      <div className="panel-head">
        <span className="panel-title flex items-center gap-2"><TrendingUp className="size-4 text-warn" />Likely to breach SLA</span>
        <span className="text-[12px] text-ink-3">predicted from history, queue and progress</span>
      </div>
      {!d ? <div className="p-4 space-y-2"><Skeleton className="h-12" /><Skeleton className="h-12" /></div> : d.slaRisk.length === 0 ? (
        <Empty icon={<CircleCheck className="size-7 text-ok" />} title="No incidents look at risk">Every open incident is on course to meet its SLA.</Empty>
      ) : (
        <ul className="divide-y divide-line">
          {d.slaRisk.map(r => (
            <li key={r.id}>
              <Link to={`/incidents/${r.id}`} className="grid grid-cols-[auto_1fr_auto] gap-3 items-center px-4 py-2.5 hover:bg-sunken/60">
                <TriageTag priority={r.priority} />
                <span className="min-w-0">
                  <span className="flex items-center gap-2"><span className="mono text-[11.5px] text-ink-3">{r.number}</span><span className="text-[13px] font-semibold truncate">{r.title}</span></span>
                  <span className="block text-[11.5px] text-ink-3 truncate">{r.assignee ?? 'Unassigned'} · {r.reasons.join(' · ') || 'running close to deadline'}</span>
                </span>
                <span className="text-right w-[92px]">
                  <span className={cx('mono text-[15px] font-semibold', r.risk >= 0.9 ? 'text-bad' : 'text-warn')}>{Math.round(r.risk * 100)}%</span>
                  <span className="block mono text-[11px] text-ink-3">{countdown(new Date(r.resolutionDueAt).getTime() - now)} left</span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function TrendChart({ d }: { d?: D }) {
  const data = d?.trend.map(t => ({ ...t, label: new Date(t.day + 'T00:00:00').toLocaleDateString(undefined, { day: 'numeric', month: 'short' }) }))
  return (
    <section className="panel">
      <div className="panel-head"><span className="panel-title">Opened vs resolved</span><span className="text-[12px] text-ink-3">last 14 days</span></div>
      <div className="p-3 h-[280px]">
        {!data ? <Skeleton className="h-full" /> : (
          <ResponsiveContainer>
            <BarChart data={data} barGap={2} barCategoryGap="22%" margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
              <CartesianGrid vertical={false} stroke="#e1e6ee" />
              <XAxis dataKey="label" tickLine={false} axisLine={{ stroke: '#c9d1dd' }} tick={{ fontSize: 11, fill: '#5b687c' }} interval="preserveStartEnd" />
              <YAxis allowDecimals={false} tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: '#5b687c' }} />
              <Tooltip cursor={{ fill: '#eef1f5' }} content={<TrendTip />} />
              <Legend iconType="square" iconSize={10} wrapperStyle={{ fontSize: 12, color: '#3c4a60' }} />
              <Bar dataKey="created" name="Opened" fill={SERIES.created} radius={[4, 4, 0, 0]} maxBarSize={18} />
              <Bar dataKey="resolved" name="Resolved" fill={SERIES.resolved} radius={[4, 4, 0, 0]} maxBarSize={18} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>
    </section>
  )
}

function TrendTip({ active, payload, label }: { active?: boolean; payload?: { payload: D['trend'][number] }[]; label?: string }) {
  if (!active || !payload?.length) return null
  const p = payload[0].payload
  return (
    <div className="panel px-3 py-2 text-[12px] shadow-[0_8px_24px_-8px_rgba(15,35,65,.3)]">
      <div className="font-bold mb-1">{label}</div>
      <div className="flex items-center gap-2"><span className="size-2.5 rounded-sm" style={{ background: SERIES.created }} />Opened <span className="mono ml-auto pl-4">{p.created}</span></div>
      <div className="flex items-center gap-2"><span className="size-2.5 rounded-sm" style={{ background: SERIES.resolved }} />Resolved <span className="mono ml-auto pl-4">{p.resolved}</span></div>
      <div className="text-ink-3 mt-1">{p.breached} opened that day missed SLA</div>
    </div>
  )
}

function PriorityMix({ d }: { d?: D }) {
  const max = Math.max(1, ...(d?.byPriority.map(p => p.value) ?? [1]))
  return (
    <section className="panel">
      <div className="panel-head"><span className="panel-title">Open by priority</span></div>
      <div className="p-4 space-y-3.5">
        {!d ? <Skeleton className="h-40" /> : d.byPriority.map(p => (
          <Link key={p.label} to={`/incidents?open=true&priority=${p.label}`} className="grid grid-cols-[110px_1fr_36px] items-center gap-3 group">
            <span className="flex items-center gap-2"><TriageTag priority={p.label as Priority} size="sm" /><span className="text-[12.5px] text-ink-2">{PRIORITY_LABEL[p.label as Priority]}</span></span>
            <span className="h-[14px] rounded-r-[4px] bg-ink-2 group-hover:bg-action transition-colors" style={{ width: `${(p.value / max) * 100}%`, minWidth: p.value ? 4 : 0 }} />
            <span className="mono text-[13px] font-semibold text-right">{p.value}</span>
          </Link>
        ))}
      </div>
    </section>
  )
}

function Workload({ d }: { d?: D }) {
  return (
    <section className="panel">
      <div className="panel-head"><span className="panel-title">Technician capacity</span><span className="text-[12px] text-ink-3">active incidents vs limit</span></div>
      {!d ? <div className="p-4 space-y-2">{Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-9" />)}</div> : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[560px]">
            <thead><tr><th className="th">Technician</th><th className="th w-[38%]">Load</th><th className="th text-right">Resolved</th><th className="th text-right">Rating</th><th className="th text-right">Misses</th></tr></thead>
            <tbody>
              {d.technicians.map(t => (
                <tr key={t.id} className="hover:bg-sunken/60">
                  <td className="td">
                    <Link to={`/incidents?assigneeId=${t.id}&open=true`} className="font-semibold text-[13px] hover:underline">{t.name}</Link>
                    <div className="text-[11.5px] text-ink-3 truncate max-w-[220px]">{t.available ? t.skills.join(', ') : 'Unavailable'}</div>
                  </td>
                  <td className="td"><CapacityBar value={t.active} max={t.max} /></td>
                  <td className="td text-right mono text-[13px]">{t.resolved}</td>
                  <td className="td text-right mono text-[13px]">{t.avgRating ?? '—'}</td>
                  <td className={cx('td text-right mono text-[13px]', t.breaches > 0 && 'text-bad')}>{t.breaches}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function Clusters({ d }: { d?: D }) {
  if (!d || d.clusters.length === 0) return null
  return (
    <section className="panel">
      <div className="panel-head"><span className="panel-title flex items-center gap-2"><Copy className="size-4 text-p2" />Correlated incidents</span></div>
      <ul className="divide-y divide-line">
        {d.clusters.map(c => (
          <li key={c.rootId} className="px-4 py-3">
            <Link to={`/incidents/${c.rootId}`} className="text-[13px] font-semibold hover:underline"><span className="mono text-ink-3 mr-2">{c.rootNumber}</span>{c.title}</Link>
            <div className="text-[12px] text-ink-3 mt-0.5">{c.service} · {c.members.length} linked report{c.members.length > 1 ? 's' : ''}: <span className="mono">{c.members.join(', ')}</span></div>
          </li>
        ))}
      </ul>
    </section>
  )
}

function Mttr({ d }: { d?: D }) {
  const max = Math.max(1, ...(d?.mttrByPriority.map(p => p.value) ?? [1]))
  return (
    <section className="panel">
      <div className="panel-head"><span className="panel-title">Mean time to resolve</span><span className="text-[12px] text-ink-3">by priority, 14 days</span></div>
      <div className="p-4 space-y-3">
        {!d ? <Skeleton className="h-28" /> : d.mttrByPriority.map(p => (
          <div key={p.label} className="grid grid-cols-[44px_1fr_84px] items-center gap-3">
            <TriageTag priority={p.label as Priority} size="sm" />
            <span className="h-[10px] rounded-r-[4px] bg-ink-2" style={{ width: `${(p.value / max) * 100}%`, minWidth: p.value ? 4 : 0 }} />
            <span className="mono text-[12.5px] text-right">{p.value ? minutes(p.value) : '—'}</span>
          </div>
        ))}
      </div>
    </section>
  )
}

function CategoryChart({ d }: { d?: D }) {
  return (
    <section className="panel">
      <div className="panel-head"><span className="panel-title">Incidents by category</span><span className="text-[12px] text-ink-3">all time</span></div>
      <div className="p-3 h-[300px]">
        {!d ? <Skeleton className="h-full" /> : (
          <ResponsiveContainer>
            <BarChart data={d.byCategory} layout="vertical" margin={{ top: 0, right: 28, bottom: 0, left: 8 }} barCategoryGap="28%">
              <CartesianGrid horizontal={false} stroke="#e1e6ee" />
              <XAxis type="number" allowDecimals={false} tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: '#5b687c' }} />
              <YAxis type="category" dataKey="label" width={104} tickLine={false} axisLine={false} tick={{ fontSize: 12, fill: '#3c4a60' }} />
              <Tooltip cursor={{ fill: '#eef1f5' }} formatter={(v: number) => [v, 'Incidents']} contentStyle={{ borderRadius: 8, borderColor: '#e1e6ee', fontSize: 12 }} />
              <Bar dataKey="value" fill="#1a3358" radius={[0, 4, 4, 0]} maxBarSize={16} label={{ position: 'right', fontSize: 11, fill: '#3c4a60' }} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>
    </section>
  )
}

function ServiceTable({ d }: { d?: D }) {
  return (
    <section className="panel">
      <div className="panel-head"><span className="panel-title">Service analysis</span><span className="text-[12px] text-ink-3">all time</span></div>
      {!d ? <Skeleton className="h-64 m-4" /> : (
        <table className="w-full">
          <thead><tr><th className="th">Service</th><th className="th text-right">Incidents</th><th className="th text-right">Open</th><th className="th text-right">SLA missed</th></tr></thead>
          <tbody>
            {d.byService.map(s => (
              <tr key={s.service} className="hover:bg-sunken/60">
                <td className="td text-[13px] font-semibold">{s.service}</td>
                <td className="td text-right mono text-[13px]">{s.total}</td>
                <td className="td text-right mono text-[13px]">{s.open}</td>
                <td className="td text-right mono text-[13px]">{s.breached}<span className="text-ink-3"> ({Math.round((s.breached / Math.max(1, s.total)) * 100)}%)</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
