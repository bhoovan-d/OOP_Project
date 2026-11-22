import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Download, FilterX, SearchX } from 'lucide-react'
import { api, qs, type Catalog, type Page, type Priority, type Summary, type UserView } from '../lib/api'
import { useUser } from '../lib/auth'
import { STATUS_LABEL } from '../lib/format'
import { IncidentTable, type SortState } from '../components/IncidentTable'
import { Empty, ErrorNote, PageHeader, Pager, SkeletonRows, PriorityTag, cx } from '../components/ui'

const STATUSES = Object.keys(STATUS_LABEL) as (keyof typeof STATUS_LABEL)[]
const FILTER_KEYS = ['q', 'status', 'priority', 'serviceId', 'categoryId', 'assigneeId', 'slaStatus', 'breached', 'unassigned', 'open', 'from', 'to']

export default function Incidents() {
  const user = useUser()
  const [sp, setSp] = useSearchParams()
  const get = (k: string) => sp.get(k) ?? ''
  const priorities = sp.getAll('priority') as Priority[]
  const page = Number(sp.get('page') ?? 0)
  const sort: SortState = { sort: sp.get('sort') ?? 'createdAt', dir: (sp.get('dir') as 'asc' | 'desc') ?? 'desc' }

  const update = (patch: Record<string, string | string[] | null>) => {
    const next = new URLSearchParams(sp)
    for (const [k, v] of Object.entries(patch)) {
      next.delete(k)
      if (Array.isArray(v)) v.forEach(x => next.append(k, x))
      else if (v) next.set(k, v)
    }
    if (!('page' in patch)) next.delete('page')
    setSp(next, { replace: true })
  }

  const toDayIso = (d: string, end = false) => (d ? new Date(`${d}T${end ? '23:59:59' : '00:00:00'}`).toISOString() : undefined)
  const params = {
    q: get('q'), status: get('status') || undefined, priority: priorities, serviceId: get('serviceId'), categoryId: get('categoryId'),
    assigneeId: get('assigneeId'), slaStatus: get('slaStatus'), breached: get('breached'), unassigned: get('unassigned'), open: get('open'),
    from: toDayIso(get('from')), to: toDayIso(get('to'), true), page, size: 20, sort: sort.sort, dir: sort.dir,
  }
  const list = useQuery({ queryKey: ['incidents', 'search', params], queryFn: () => api<Page<Summary>>(`/incidents${qs(params)}`), placeholderData: p => p })
  const catalog = useQuery({ queryKey: ['catalog'], queryFn: () => api<Catalog>('/catalog'), staleTime: 300_000 })
  const techs = useQuery({ queryKey: ['technicians'], queryFn: () => api<UserView[]>('/admin/users?role=TECHNICIAN'), enabled: user.role === 'ADMIN' })
  const active = FILTER_KEYS.filter(k => sp.getAll(k).length > 0).length

  const title = get('breached') ? 'SLA violations' : get('unassigned') ? 'Unassigned incidents' : 'Incidents'
  const exportCsv = async () => {
    const all = await api<Page<Summary>>(`/incidents${qs({ ...params, page: 0, size: 100 })}`)
    const head = ['Number', 'Title', 'Priority', 'Status', 'Service', 'Category', 'Technician', 'Reporter', 'SLA', 'Created', 'Resolution due']
    const esc = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`
    const lines = all.content.map(r => [r.number, r.title, r.priority, r.status, r.service?.name, r.category?.name, r.assignee?.name, r.reporter?.name,
      r.breached ? 'BREACHED' : r.slaStatus, r.createdAt, r.resolutionDueAt].map(esc).join(','))
    const url = URL.createObjectURL(new Blob([[head.join(','), ...lines].join('\n')], { type: 'text/csv' }))
    Object.assign(document.createElement('a'), { href: url, download: `incidents-${new Date().toISOString().slice(0, 10)}.csv` }).click()
    URL.revokeObjectURL(url)
  }

  return (
    <>
      <PageHeader title={title} sub="Filter by any combination; the URL keeps your view, so you can share it."
                  actions={<button className="btn-secondary" onClick={exportCsv}><Download className="size-4" />Export CSV</button>} />
      <div className="panel">
        <div className="p-4 border-b border-line space-y-3">
          <div className="flex flex-wrap gap-3 items-end">
            <label className="flex-1 min-w-[220px]"><span className="field-label">Search</span>
              <input className="input" placeholder="Title, description or INC number" defaultValue={get('q')} key={get('q')}
                     onKeyDown={e => e.key === 'Enter' && update({ q: (e.target as HTMLInputElement).value })}
                     onBlur={e => e.target.value !== get('q') && update({ q: e.target.value })} />
            </label>
            <Select label="Status" value={get('status')} onChange={v => update({ status: v, open: null })}
                    options={[['', 'Any status'], ...STATUSES.map(s => [s, STATUS_LABEL[s]] as [string, string])]} />
            <Select label="Service" value={get('serviceId')} onChange={v => update({ serviceId: v })}
                    options={[['', 'All services'], ...(catalog.data?.services ?? []).map(s => [String(s.id), s.name] as [string, string])]} />
            <Select label="Category" value={get('categoryId')} onChange={v => update({ categoryId: v })}
                    options={[['', 'All categories'], ...(catalog.data?.categories ?? []).map(c => [String(c.id), c.name] as [string, string])]} />
            {user.role === 'ADMIN' ? (
              <Select label="Technician" value={get('assigneeId')} onChange={v => update({ assigneeId: v })}
                      options={[['', 'Anyone'], ...(techs.data ?? []).map(t => [String(t.id), t.fullName] as [string, string])]} />
            ) : (
              <Select label="Technician" value={get('assigneeId')} onChange={v => update({ assigneeId: v })} options={[['', 'Anyone'], [String(user.id), 'Me']]} />
            )}
          </div>
          <div className="flex flex-wrap gap-x-5 gap-y-3 items-end">
            <fieldset><legend className="field-label">Priority</legend>
              <div className="flex gap-1.5">
                {(['P1', 'P2', 'P3', 'P4'] as Priority[]).map(p => {
                  const on = priorities.includes(p)
                  return (
                    <button key={p} type="button" aria-pressed={on} onClick={() => update({ priority: on ? priorities.filter(x => x !== p) : [...priorities, p] })}
                            className={cx('rounded-[4px] p-1 border transition-colors', on ? 'border-ink bg-sunken' : 'border-transparent opacity-55 hover:opacity-100')}>
                      <PriorityTag priority={p} size="sm" />
                    </button>
                  )
                })}
              </div>
            </fieldset>
            <Select label="SLA" value={get('slaStatus')} onChange={v => update({ slaStatus: v })}
                    options={[['', 'Any'], ['ON_TRACK', 'On track'], ['AT_RISK', 'At risk'], ['BREACHED', 'Breached'], ['PAUSED', 'Paused'], ['MET', 'Met']]} />
            <Select label="State" value={get('open')} onChange={v => update({ open: v, status: null })} options={[['', 'Open & closed'], ['true', 'Open only'], ['false', 'Closed only']]} />
            <label><span className="field-label">From</span><input type="date" className="input w-[150px]" value={get('from')} onChange={e => update({ from: e.target.value })} /></label>
            <label><span className="field-label">To</span><input type="date" className="input w-[150px]" value={get('to')} onChange={e => update({ to: e.target.value })} /></label>
            <Toggle label="SLA missed" on={!!get('breached')} onChange={on => update({ breached: on ? 'true' : null })} />
            <Toggle label="Unassigned" on={!!get('unassigned')} onChange={on => update({ unassigned: on ? 'true' : null })} />
            {active > 0 && <button className="btn-ghost ml-auto" onClick={() => setSp(new URLSearchParams(), { replace: true })}><FilterX className="size-4" />Clear {active} filter{active > 1 ? 's' : ''}</button>}
          </div>
        </div>
        {list.error ? <div className="p-4"><ErrorNote error={list.error} /></div> : list.isLoading ? <SkeletonRows rows={8} cols={6} /> : list.data!.content.length === 0 ? (
          <Empty icon={<SearchX className="size-8" />} title="No incidents match these filters">Try removing a filter or widening the date range.</Empty>
        ) : (
          <div className={cx(list.isFetching && 'opacity-70 transition-opacity')}>
            <IncidentTable rows={list.data!.content} cols={['service', 'assignee', 'reporter']} sort={sort} onSort={s => update({ sort: s.sort, dir: s.dir })} />
            <Pager page={page} totalPages={list.data!.totalPages} total={list.data!.totalElements} onPage={p => update({ page: String(p) })} />
          </div>
        )}
      </div>
    </>
  )
}

function Select({ label, value, onChange, options }: { label: string; value: string; onChange: (v: string) => void; options: [string, string][] }) {
  return (
    <label className="min-w-[150px]"><span className="field-label">{label}</span>
      <select className="input" value={value} onChange={e => onChange(e.target.value)}>
        {options.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
      </select>
    </label>
  )
}

function Toggle({ label, on, onChange }: { label: string; on: boolean; onChange: (on: boolean) => void }) {
  return (
    <label className="inline-flex items-center gap-2 h-9 cursor-pointer text-[13.5px] font-semibold text-ink-2">
      <input type="checkbox" className="size-4 accent-[var(--color-action)]" checked={on} onChange={e => onChange(e.target.checked)} />{label}
    </label>
  )
}
