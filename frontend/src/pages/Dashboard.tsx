import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { CircleAlert, CircleCheck, Copy, OctagonAlert, TrendingUp } from 'lucide-react'
import { api, qs, type Dashboard as D, type Detail, type Page, type Priority, type Summary } from '../lib/api'
import { minutes, PRIORITY_LABEL, timeLeft } from '../lib/format'
import { useNow } from '../lib/live'
import { Board, BoardCount, CapacityBar, Empty, ErrorNote, NowLead, NowPlate, PageHeader, Skeleton, SkeletonRows, PriorityTag, cx } from '../components/ui'
import { IncidentTable } from '../components/IncidentTable'

// Opened is solid board green, resolved an outlined plate: told apart by fill, not by hue.
const SERIES = { created: '#14553f', resolved: '#f7f7f2', stroke: '#14553f' }

export default function Dashboard() {
  const q = useQuery({ queryKey: ['dashboard'], queryFn: () => api<D>('/reports/dashboard?days=14'), refetchInterval: 30_000 })
  if (q.error) return <ErrorNote error={q.error} />
  const d = q.data

  return (
    <>
      <PageHeader title="Operations board" sub="Live incidents, SLA health and team capacity. Plates update as events happen." />
      <Kpis d={d} />
      <OpenQueue />
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

/** The operations board: one reversed plate for what needs attention now, the live counts hung beside it. */
function Kpis({ d }: { d?: D }) {
  const o = d?.overview
  const now = useNow()
  const mttr = o ? (o.mttrMinutes < 60 ? { v: Math.round(o.mttrMinutes), u: 'm' } : { v: (o.mttrMinutes / 60).toFixed(1), u: 'h' }) : undefined
  const major = d?.clusters[0]
  const risk = d?.slaRisk[0]
  const rootQ = useQuery({ queryKey: ['incident', major?.rootId], queryFn: () => api<Detail>(`/incidents/${major!.rootId}`), enabled: !!major })
  const root = rootQ.data
  const rootDue = root?.summary.resolutionDueAt ? new Date(root.summary.resolutionDueAt).getTime() : null
  return (
    <>
      <Board label="Operations board" className="grid-cols-2 sm:grid-cols-3 xl:grid-cols-[minmax(0,1.7fr)_repeat(6,minmax(0,1fr))] items-stretch">
        <div className="col-span-2 sm:col-span-3 xl:col-span-1">
          {!d ? <Skeleton className="h-full min-h-[104px] bg-board-2" />
            : major ? <NowPlate to={`/incidents/${major.rootId}`}
                                head={<>{root && <PriorityTag priority={root.summary.priority} size="lg" />}{major.rootNumber}{rootDue != null && <span className="basis-full">{timeLeft(rootDue - now)}</span>}</>}
                                detail={<><NowLead tone="danger">Major incident</NowLead>&nbsp;· {major.service}&nbsp;· {major.title}&nbsp;· {major.members.length} linked report{major.members.length > 1 ? 's' : ''}</>} />
            : risk ? <NowPlate to={`/incidents/${risk.id}`}
                               head={<><PriorityTag priority={risk.priority} size="lg" />{risk.number}<span className="basis-full">{timeLeft(new Date(risk.resolutionDueAt).getTime() - now)}</span></>}
                               detail={<><NowLead tone="risk">Most likely to breach</NowLead>&nbsp;· {risk.title}&nbsp;· {risk.assignee ?? 'Unassigned'}</>} />
            : <NowPlate head="Nothing at risk" detail={<><NowLead tone="clear">All clear</NowLead>&nbsp;· Every open incident is on course to meet its SLA.</>} />}
        </div>
        <BoardCount label="Open" value={o?.open} to="/incidents?open=true" />
        <BoardCount label="Critical" value={o?.criticalOpen} tone={o && o.criticalOpen > 0 ? 'text-p1' : undefined} to="/incidents?open=true&priority=P1" />
        <BoardCount label="At risk" value={o?.atRisk} tone={o && o.atRisk > 0 ? 'text-warn' : undefined} to="/incidents?slaStatus=AT_RISK" />
        <BoardCount label="Unassigned" value={o?.unassigned} to="/incidents?unassigned=true&open=true" />
        <BoardCount label="SLA met" value={o?.slaCompliancePct} unit="%" />
        <BoardCount label="Avg resolve" value={mttr?.v} unit={mttr?.u} />
      </Board>
      <p className="mt-2.5 text-[13px] text-ink-3 flex flex-wrap gap-x-5 gap-y-1">
        <Link to="/incidents?breached=true" className="hover:text-ink hover:underline"><span className="mono font-bold text-[15px] text-ink">{o?.breached ?? '–'}</span> SLA violations all time</Link>
        <span><span className="mono font-bold text-[15px] text-ink">{o ? o.avgRating : '–'}</span> / 5 satisfaction{o ? ` from ${o.ratings} ratings` : ''}</span>
        <span>SLA met and average resolve time cover the last 14 days</span>
      </p>
    </>
  )
}

/** The working queue: open incidents by time left, each with its ball-slot SLA. */
function OpenQueue() {
  const q = useQuery({ queryKey: ['incidents', 'dash-queue'], queryFn: () => api<Page<Summary>>(`/incidents${qs({ open: true, sort: 'resolutionDueAt', dir: 'asc', size: 8 })}`), refetchInterval: 30_000 })
  return (
    <section className="panel mt-5">
      <div className="panel-head">
        <span className="panel-title">Open queue</span>
        <span className="flex items-center gap-4 text-[12.5px] text-ink-3">by time left<Link to="/incidents?open=true" className="text-[13px] font-semibold text-action hover:underline">View all</Link></span>
      </div>
      {q.isLoading ? <SkeletonRows rows={5} /> : !q.data?.content.length
        ? <Empty title="No open incidents">New reports appear here the moment they are triaged.</Empty>
        : <IncidentTable rows={q.data.content} cols={['service', 'assignee']} />}
    </section>
  )
}

/** Signature: services on the board, each wearing its health like a triage tag. */
function ServiceBoard({ d }: { d?: D }) {
  const icon = { OUTAGE: <OctagonAlert className="size-4" />, DEGRADED: <CircleAlert className="size-4" />, HEALTHY: <CircleCheck className="size-4" /> }
  const tone = { OUTAGE: 'bg-p1 text-white', DEGRADED: 'bg-p3 text-ink', HEALTHY: 'bg-[#e3f1e8] text-ok' }
  const word = { OUTAGE: 'Outage', DEGRADED: 'Degraded', HEALTHY: 'Healthy' }
  return (
    <section className="panel">
      <div className="panel-head"><span className="panel-title">Service health</span><span className="text-[12.5px] text-ink-3">from open incidents, last 60 min</span></div>
      {!d ? <div className="p-4 space-y-2">{Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-9" />)}</div> : (
        <ul className="grid sm:grid-cols-2">
          {d.serviceHealth.map(s => (
            <li key={s.serviceId} className="border-b border-line sm:odd:border-r">
              <Link to={`/incidents?serviceId=${s.serviceId}&open=true`} className="flex items-center gap-3 px-4 py-2.5 hover:bg-sunken/60">
                {s.health === 'HEALTHY'
                  ? <span className="w-[104px] pl-2 text-[13px] text-ink-3">Healthy</span>
                  : <span className={cx('inline-flex items-center gap-1.5 h-6 px-2 rounded-[2px] text-[12px] font-bold uppercase tracking-[0.06em] w-[104px]', tone[s.health])}>{icon[s.health]}{word[s.health]}</span>}
                <span className="min-w-0 flex-1">
                  <span className="block text-[14px] font-semibold truncate">{s.service}</span>
                  <span className="block text-[12px] text-ink-3">{s.criticality.replace('_', ' ').toLowerCase()}</span>
                </span>
                <span className="text-right"><span className="mono text-[20px] font-bold leading-none">{s.open}</span><span className="block text-[11.5px] text-ink-3">{s.recent > 0 ? `${s.recent} new` : 'open'}</span></span>
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
        <span className="text-[12.5px] text-ink-3">predicted from history, queue and progress</span>
      </div>
      {!d ? <div className="p-4 space-y-2"><Skeleton className="h-12" /><Skeleton className="h-12" /></div> : d.slaRisk.length === 0 ? (
        <Empty icon={<CircleCheck className="size-7 text-ok" />} title="No incidents look at risk">Every open incident is on course to meet its SLA.</Empty>
      ) : (
        <ul className="divide-y divide-line">
          {d.slaRisk.map(r => (
            <li key={r.id}>
              <Link to={`/incidents/${r.id}`} className="grid grid-cols-[auto_1fr_auto] gap-3 items-center px-4 py-2.5 hover:bg-sunken/60">
                <PriorityTag priority={r.priority} />
                <span className="min-w-0">
                  <span className="flex items-center gap-2"><span className="mono text-[16px] font-bold leading-none">{r.number}</span><span className="text-[14px] font-semibold truncate">{r.title}</span></span>
                  <span className="block text-[12.5px] text-ink-3 truncate">{r.assignee ?? 'Unassigned'} · {r.reasons.join(' · ') || 'running close to deadline'}</span>
                </span>
                <span className="text-right w-[92px]">
                  <span className={cx('mono text-[22px] font-bold leading-none', r.risk >= 0.9 ? 'text-bad' : 'text-warn')}>{Math.round(r.risk * 100)}%</span>
                  <span className="block text-[12px] text-ink-3 mt-0.5 tnum">{timeLeft(new Date(r.resolutionDueAt).getTime() - now, true)}</span>
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
      <div className="panel-head"><span className="panel-title">Opened vs resolved</span><span className="flex items-center gap-3 text-[12.5px] text-ink-3"><span className="flex items-center gap-1.5"><span className="size-2.5 bg-board-2" />Opened</span><span className="flex items-center gap-1.5"><span className="size-2.5 border-[1.5px] border-board-2 bg-plate" />Resolved</span>last 14 days</span></div>
      <div className="p-3 h-[280px]">
        {!data ? <Skeleton className="h-full" /> : (
          <ResponsiveContainer>
            <BarChart data={data} barGap={2} barCategoryGap="22%" margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
              <CartesianGrid vertical={false} stroke="#d8dfd8" />
              <XAxis dataKey="label" tickLine={false} axisLine={{ stroke: '#b4c0b7' }} tick={{ fontSize: 12, fill: '#56635c' }} interval="preserveStartEnd" />
              <YAxis allowDecimals={false} tickLine={false} axisLine={false} tick={{ fontSize: 12, fill: '#56635c' }} />
              <Tooltip cursor={{ fill: '#f1f4f0' }} content={<TrendTip />} />
              <Bar isAnimationActive={false} dataKey="created" name="Opened" fill={SERIES.created} radius={[2, 2, 0, 0]} maxBarSize={18} />
              <Bar isAnimationActive={false} dataKey="resolved" name="Resolved" fill={SERIES.resolved} stroke={SERIES.stroke} strokeWidth={1.5} radius={[2, 2, 0, 0]} maxBarSize={18} />
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
    <div className="panel px-3 py-2 text-[13px] shadow-[0_8px_24px_-8px_rgba(8,30,22,.35)]">
      <div className="font-display text-[16px] font-bold mb-1">{label}</div>
      <div className="flex items-center gap-2"><span className="size-2.5 bg-board-2" />Opened <span className="mono ml-auto pl-4">{p.created}</span></div>
      <div className="flex items-center gap-2"><span className="size-2.5 border-[1.5px] border-board-2 bg-plate" />Resolved <span className="mono ml-auto pl-4">{p.resolved}</span></div>
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
          <Link key={p.label} to={`/incidents?open=true&priority=${p.label}`} className="grid grid-cols-[110px_minmax(0,1fr)_36px] items-center gap-3 group">
            <span className="flex items-center gap-2"><PriorityTag priority={p.label as Priority} size="sm" /><span className="text-[13px] text-ink-2">{PRIORITY_LABEL[p.label as Priority]}</span></span>
            <span className="min-w-0"><span className="block h-[14px] rounded-r-[2px] bg-board-2 group-hover:bg-ink" style={{ width: `${(p.value / max) * 100}%`, minWidth: p.value ? 4 : 0 }} /></span>
            <span className="mono text-[18px] font-bold text-right leading-none">{p.value}</span>
          </Link>
        ))}
      </div>
    </section>
  )
}

function Workload({ d }: { d?: D }) {
  return (
    <section className="panel">
      <div className="panel-head"><span className="panel-title">Technician capacity</span><span className="text-[12.5px] text-ink-3">active incidents vs limit</span></div>
      {!d ? <div className="p-4 space-y-2">{Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-9" />)}</div> : (
        <div className="overflow-x-auto">
          <table className="w-full sm:min-w-[560px]">
            <thead><tr><th className="th">Technician</th><th className="th w-[44%] sm:w-[38%]">Load</th><th className="th text-right hidden sm:table-cell">Resolved</th><th className="th text-right hidden sm:table-cell">Rating</th><th className="th text-right">Misses</th></tr></thead>
            <tbody>
              {d.technicians.map(t => (
                <tr key={t.id} className="hover:bg-sunken/60">
                  <td className="td">
                    <Link to={`/incidents?assigneeId=${t.id}&open=true`} className="font-semibold text-[14px] hover:underline">{t.name}</Link>
                    <div className="text-[12px] text-ink-3 truncate max-w-[120px] sm:max-w-[220px]">{t.available ? t.skills.join(', ') : 'Unavailable'}</div>
                  </td>
                  <td className="td"><CapacityBar value={t.active} max={t.max} /></td>
                  <td className="td text-right mono text-[16px] font-semibold hidden sm:table-cell">{t.resolved}</td>
                  <td className="td text-right mono text-[16px] font-semibold hidden sm:table-cell">{t.avgRating ?? '—'}</td>
                  <td className={cx('td text-right mono text-[16px] font-semibold', t.breaches > 0 && 'text-bad')}>{t.breaches}</td>
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
      <div className="panel-head"><span className="panel-title flex items-center gap-2"><Copy className="size-4 text-warn" />Correlated incidents</span></div>
      <ul className="divide-y divide-line">
        {d.clusters.map(c => (
          <li key={c.rootId} className="px-4 py-3">
            <Link to={`/incidents/${c.rootId}`} className="text-[14px] font-semibold hover:underline"><span className="mono text-[16px] font-bold mr-2">{c.rootNumber}</span>{c.title}</Link>
            <div className="text-[12.5px] text-ink-3 mt-0.5">{c.service} · {c.members.length} linked report{c.members.length > 1 ? 's' : ''}: <span className="mono">{c.members.join(', ')}</span></div>
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
      <div className="panel-head"><span className="panel-title">Mean time to resolve</span><span className="text-[12.5px] text-ink-3">by priority, 14 days</span></div>
      <div className="p-4 space-y-3">
        {!d ? <Skeleton className="h-28" /> : d.mttrByPriority.map(p => (
          <div key={p.label} className="grid grid-cols-[44px_minmax(0,1fr)_84px] items-center gap-3">
            <PriorityTag priority={p.label as Priority} size="sm" />
            <span className="min-w-0"><span className="block h-[10px] rounded-r-[2px] bg-board-2" style={{ width: `${(p.value / max) * 100}%`, minWidth: p.value ? 4 : 0 }} /></span>
            <span className="mono text-[16px] font-semibold text-right">{p.value ? minutes(p.value) : '—'}</span>
          </div>
        ))}
      </div>
    </section>
  )
}

function CategoryChart({ d }: { d?: D }) {
  return (
    <section className="panel">
      <div className="panel-head"><span className="panel-title">Incidents by category</span><span className="text-[12.5px] text-ink-3">all time</span></div>
      <div className="p-3 h-[300px]">
        {!d ? <Skeleton className="h-full" /> : (
          <ResponsiveContainer>
            <BarChart data={d.byCategory} layout="vertical" margin={{ top: 0, right: 28, bottom: 0, left: 8 }} barCategoryGap="28%">
              <CartesianGrid horizontal={false} stroke="#d8dfd8" />
              <XAxis type="number" allowDecimals={false} tickLine={false} axisLine={false} tick={{ fontSize: 12, fill: '#56635c' }} />
              <YAxis type="category" dataKey="label" width={104} tickLine={false} axisLine={false} tick={{ fontSize: 13, fill: '#33403a' }} />
              <Tooltip cursor={{ fill: '#f1f4f0' }} formatter={(v: number) => [v, 'Incidents']} contentStyle={{ borderRadius: 4, borderColor: '#d8dfd8', fontSize: 13, fontFamily: 'Barlow' }} />
              <Bar isAnimationActive={false} dataKey="value" fill="#14553f" radius={[0, 2, 2, 0]} maxBarSize={16} label={{ position: 'right', fontSize: 13, fill: '#33403a', fontFamily: 'Barlow Condensed', fontWeight: 700 }} />
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
      <div className="panel-head"><span className="panel-title">Service analysis</span><span className="text-[12.5px] text-ink-3">all time</span></div>
      {!d ? <Skeleton className="h-64 m-4" /> : (
        <table className="w-full">
          <thead><tr><th className="th">Service</th><th className="th text-right">Incidents</th><th className="th text-right">Open</th><th className="th text-right">SLA missed</th></tr></thead>
          <tbody>
            {d.byService.map(s => (
              <tr key={s.service} className="hover:bg-sunken/60">
                <td className="td text-[14px] font-semibold">{s.service}</td>
                <td className="td text-right mono text-[16px] font-semibold">{s.total}</td>
                <td className="td text-right mono text-[16px] font-semibold">{s.open}</td>
                <td className="td text-right mono text-[16px] font-semibold">{s.breached}<span className="text-ink-3 font-normal"> ({Math.round((s.breached / Math.max(1, s.total)) * 100)}%)</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
