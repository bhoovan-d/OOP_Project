import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { CheckCheck, Inbox } from 'lucide-react'
import { api, qs, type MyStats, type Page, type Summary } from '../lib/api'
import { useUser } from '../lib/auth'
import { IncidentTable } from '../components/IncidentTable'
import { CapacityBar, Empty, PageHeader, Skeleton, SkeletonRows } from '../components/ui'
import { STATUS_LABEL } from '../lib/format'
import type { Status } from '../lib/api'

export default function Queue() {
  const user = useUser()
  const stats = useQuery({ queryKey: ['mystats'], queryFn: () => api<MyStats>('/reports/me') })
  const mine = useQuery({
    queryKey: ['incidents', 'queue', user.id],
    queryFn: () => api<Page<Summary>>(`/incidents${qs({ assigneeId: user.id, open: true, sort: 'resolutionDueAt', dir: 'asc', size: 50 })}`),
  })
  const pool = useQuery({
    queryKey: ['incidents', 'pool'],
    queryFn: () => api<Page<Summary>>(`/incidents${qs({ unassigned: true, open: true, sort: 'priority', dir: 'asc', size: 8 })}`),
  })
  // P1 first, then the nearest deadline: the board reads top-down as "what to touch next"
  const rows = [...(mine.data?.content ?? [])].sort((a, b) =>
    (a.priority ?? 'P9').localeCompare(b.priority ?? 'P9') || (a.resolutionDueAt ?? '').localeCompare(b.resolutionDueAt ?? ''))
  const s = stats.data

  return (
    <>
      <PageHeader title="My queue" sub="Ordered by priority, then by the nearest SLA deadline." />

      <section className="panel p-5 mb-5 grid gap-6 md:grid-cols-[minmax(0,1.3fr)_repeat(3,minmax(0,1fr))] items-center">
        <div>
          <div className="text-[12.5px] font-semibold text-ink-3 mb-2">Workload</div>
          {s ? <CapacityBar value={s.active} max={s.maxWorkload} /> : <Skeleton className="h-4" />}
          <div className="text-[12px] text-ink-3 mt-1.5">
            {s ? (s.active >= s.maxWorkload ? 'At capacity: new incidents will route to colleagues.' : `${s.maxWorkload - s.active} slot${s.maxWorkload - s.active === 1 ? '' : 's'} free for new incidents.`) : ' '}
          </div>
        </div>
        <Stat label="Resolved this week" value={s?.resolvedThisWeek} />
        <Stat label="Average rating" value={s?.avgRating != null ? `${s.avgRating} / 5` : '—'} />
        <Stat label="SLA misses (all time)" value={s?.breaches} tone={s && s.breaches > 0 ? 'text-bad' : undefined} />
      </section>

      <section className="panel mb-5">
        <div className="panel-head">
          <span className="panel-title">Assigned to me</span>
          <span className="text-[12px] text-ink-3">{s?.byStatus.map(b => `${b.value} ${STATUS_LABEL[b.label as Status].toLowerCase()}`).join(' · ')}</span>
        </div>
        {mine.isLoading ? <SkeletonRows rows={4} /> : rows.length === 0
          ? <Empty icon={<CheckCheck className="size-8" />} title="Your queue is clear">New incidents that match your skills will land here automatically. You can also take one from the unassigned pool below.</Empty>
          : <IncidentTable rows={rows} cols={['service', 'reporter']} />}
      </section>

      <section className="panel">
        <div className="panel-head">
          <span className="panel-title">Waiting for a technician</span>
          <Link to="/incidents?unassigned=true" className="text-[12.5px] font-semibold text-action hover:underline">View all</Link>
        </div>
        {pool.isLoading ? <SkeletonRows rows={3} /> : !pool.data?.content.length
          ? <Empty icon={<Inbox className="size-8" />} title="No unassigned incidents">Everything is routed. Nice.</Empty>
          : <IncidentTable rows={pool.data.content} cols={['service', 'category']} />}
      </section>
    </>
  )
}

function Stat({ label, value, tone }: { label: string; value?: number | string; tone?: string }) {
  return (
    <div>
      <div className="text-[12.5px] font-semibold text-ink-3">{label}</div>
      {value === undefined ? <Skeleton className="h-7 w-16 mt-1" /> : <div className={`mono text-[24px] font-semibold mt-0.5 ${tone ?? 'text-ink'}`}>{value}</div>}
    </div>
  )
}
