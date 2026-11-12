import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ClipboardList, Plus } from 'lucide-react'
import { api, qs, type Page, type Summary } from '../lib/api'
import { useUser } from '../lib/auth'
import { IncidentTable } from '../components/IncidentTable'
import { Empty, ErrorNote, PageHeader, Pager, SkeletonRows, cx } from '../components/ui'

const TABS = [
  { key: 'open', label: 'Open', params: { open: true } },
  { key: 'action', label: 'Needs your reply', params: { status: ['AWAITING_INFO', 'RESOLVED'] } },
  { key: 'closed', label: 'Closed', params: { open: false } },
  { key: 'all', label: 'All', params: {} },
] as const

export default function MyIncidents() {
  const user = useUser()
  const [sp] = useSearchParams()
  const [tab, setTab] = useState<(typeof TABS)[number]['key']>(sp.get('q') ? 'all' : 'open')
  const [page, setPage] = useState(0)
  const q = sp.get('q') ?? ''
  const params = { ...TABS.find(t => t.key === tab)!.params, q, page, size: 15 }
  const list = useQuery({ queryKey: ['incidents', 'mine', params], queryFn: () => api<Page<Summary>>(`/incidents${qs(params)}`), placeholderData: p => p })
  const waiting = useQuery({ queryKey: ['incidents', 'mine', 'waiting'], queryFn: () => api<Page<Summary>>(`/incidents${qs({ status: ['AWAITING_INFO', 'RESOLVED'], size: 1 })}`) })
  const waitingCount = waiting.data?.totalElements ?? 0

  return (
    <>
      <PageHeader title={`Hi ${user.fullName.split(' ')[0]}`} sub={q ? <>Results for “{q}”</> : 'Everything you have reported to IT, and where each one stands.'}
                  actions={<Link to="/report" className="btn-primary sm:hidden"><Plus className="size-4" />Report</Link>} />
      <div className="panel">
        <div className="flex items-center gap-1 px-2 border-b border-line overflow-x-auto" role="tablist">
          {TABS.map(t => (
            <button key={t.key} role="tab" aria-selected={tab === t.key} onClick={() => { setTab(t.key); setPage(0) }}
                    className={cx('relative h-11 px-3 text-[13.5px] font-semibold whitespace-nowrap', tab === t.key ? 'text-ink' : 'text-ink-3 hover:text-ink')}>
              {t.label}
              {t.key === 'action' && waitingCount > 0 && <span className="ml-1.5 mono text-[11px] px-1.5 py-0.5 rounded-full bg-p2 text-white">{waitingCount}</span>}
              {tab === t.key && <span className="absolute inset-x-2 -bottom-px h-[2px] bg-action rounded-full" />}
            </button>
          ))}
        </div>
        {list.error ? <div className="p-4"><ErrorNote error={list.error} /></div> : list.isLoading ? <SkeletonRows /> : list.data!.content.length === 0 ? (
          <Empty icon={<ClipboardList className="size-8" />} title={tab === 'open' ? 'Nothing open right now' : tab === 'action' ? 'Nothing needs your reply' : 'No incidents here'}>
            Something not working? <Link to="/report" className="text-action font-semibold hover:underline">Report an incident</Link> and it will be triaged and routed in seconds.
          </Empty>
        ) : (
          <>
            <IncidentTable rows={list.data!.content} cols={['service', 'assignee']} />
            <Pager page={page} totalPages={list.data!.totalPages} total={list.data!.totalElements} onPage={setPage} />
          </>
        )}
      </div>
    </>
  )
}
