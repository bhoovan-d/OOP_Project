import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { CheckCheck, Inbox } from 'lucide-react'
import { api, qs, type MyStats, type Page, type Summary } from '../lib/api'
import { useUser } from '../lib/auth'
import { IncidentTable } from '../components/IncidentTable'
import { Board, BoardCount, CapacityBar, Empty, NowLead, NowPlate, PageHeader, PriorityTag, Skeleton, SkeletonRows } from '../components/ui'
import { useNow } from '../lib/live'
import { STATUS_LABEL, timeLeft } from '../lib/format'
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
  const next = rows[0]
  const now = useNow()

  return (
    <>
      <PageHeader title="My queue" sub="Ordered by priority, then by the nearest SLA deadline." />

      <Board label="My board" className="grid-cols-2 sm:grid-cols-4 xl:grid-cols-[minmax(0,1.7fr)_repeat(4,minmax(0,1fr))] mb-2.5">
        <div className="col-span-2 sm:col-span-4 xl:col-span-1">
          {mine.isLoading ? <Skeleton className="h-full min-h-[104px] bg-board-2" /> : next
            ? <NowPlate to={`/incidents/${next.id}`}
                        head={<><PriorityTag priority={next.priority} size="lg" />{next.number}{next.resolutionDueAt && <span className="basis-full">{timeLeft(new Date(next.resolutionDueAt).getTime() - now)}</span>}</>}
                        detail={<><NowLead tone="danger">Work on next</NowLead>&nbsp;· {next.title}&nbsp;· {next.service?.name}</>} />
            : <NowPlate head="Nothing assigned" detail={<><NowLead tone="clear">Queue clear</NowLead>&nbsp;· Take one from the unassigned pool below.</>} />}
        </div>
        <BoardCount label="Active" value={s ? `${s.active}/${s.maxWorkload}` : undefined} tone={s && s.active >= s.maxWorkload ? 'text-p1' : undefined} />
        <BoardCount label="Resolved · week" value={s?.resolvedThisWeek} />
        <BoardCount label="Avg rating" value={s ? (s.avgRating ?? '–') : undefined} />
        <BoardCount label="SLA misses" value={s?.breaches} tone={s && s.breaches > 0 ? 'text-p1' : undefined} />
      </Board>
      <div className="mb-5 flex items-center gap-4 text-[13px] text-ink-3">
        <span className="w-[220px] shrink-0">{s ? <CapacityBar value={s.active} max={s.maxWorkload} /> : <Skeleton className="h-4" />}</span>
        <span>{s ? (s.active >= s.maxWorkload ? 'At capacity: new incidents route to colleagues.' : `${s.maxWorkload - s.active} slot${s.maxWorkload - s.active === 1 ? '' : 's'} free for new incidents.`) : ' '}</span>
      </div>

      <section className="panel mb-5">
        <div className="panel-head">
          <span className="panel-title">Assigned to me</span>
          <span className="text-[12.5px] text-ink-3">{s?.byStatus.map(b => `${b.value} ${STATUS_LABEL[b.label as Status].toLowerCase()}`).join(' · ')}</span>
        </div>
        {mine.isLoading ? <SkeletonRows rows={4} /> : rows.length === 0
          ? <Empty icon={<CheckCheck className="size-8" />} title="Your queue is clear">New incidents that match your skills will land here automatically. You can also take one from the unassigned pool below.</Empty>
          : <IncidentTable rows={rows} cols={['service', 'reporter']} />}
      </section>

      <section className="panel">
        <div className="panel-head">
          <span className="panel-title">Waiting for a technician</span>
          <Link to="/incidents?unassigned=true" className="text-[13px] font-semibold text-action hover:underline">View all</Link>
        </div>
        {pool.isLoading ? <SkeletonRows rows={3} /> : !pool.data?.content.length
          ? <Empty icon={<Inbox className="size-8" />} title="No unassigned incidents">Everything is routed. Nice.</Empty>
          : <IncidentTable rows={pool.data.content} cols={['service', 'category']} />}
      </section>
    </>
  )
}
