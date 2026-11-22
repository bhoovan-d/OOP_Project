import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  ArrowLeft, Check, CircleCheck, CornerDownRight, GitMerge, Lightbulb, Lock, MessageSquare, Pencil, Siren, Star, UserPlus, Users,
} from 'lucide-react'
import { ApiError, api, patch, post, type AuditView, type Detail, type Impact, type Recommendation, type Similar, type Status, type Urgency, type UserView } from '../lib/api'
import { useUser } from '../lib/auth'
import { useNow } from '../lib/live'
import { LIFECYCLE, PRIORITY_LABEL, STATUS_LABEL, ago, countdown, fmtDateTime, fmtTime, minutes, titleCase } from '../lib/format'
import { CapacityBar, Dialog, Empty, ErrorNote, Field, Skeleton, Spinner, StatusBadge, PriorityTag, cx } from '../components/ui'

type ActionKey = 'ASSIGN' | 'REQUEST_INFO' | 'PROVIDE_INFO' | 'RESOLVE' | 'REOPEN' | 'CLOSE' | 'CANCEL' | 'MERGE' | 'FEEDBACK' | 'EDIT'

export default function IncidentDetail() {
  const id = Number(useParams().id)
  const user = useUser()
  const staff = user.role !== 'EMPLOYEE'
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['incident', id], queryFn: () => api<Detail>(`/incidents/${id}`), enabled: Number.isFinite(id) })
  const timeline = useQuery({ queryKey: ['incident', id, 'timeline'], queryFn: () => api<AuditView[]>(`/incidents/${id}/timeline`), enabled: q.isSuccess })
  const [dialog, setDialog] = useState<ActionKey | null>(null)

  const run = useMutation({
    mutationFn: ({ path, body }: { path: string; body?: unknown }) => post<Detail>(`/incidents/${id}/${path}`, body),
    onSuccess: d => {
      qc.setQueryData(['incident', id], d)
      qc.invalidateQueries({ queryKey: ['incident', id, 'timeline'] })
      qc.invalidateQueries({ queryKey: ['incidents'] })
      setDialog(null)
    },
    onError: e => toast.error((e as Error).message),
  })

  if (q.isLoading) return <DetailSkeleton />
  if (q.error) return (
    <div className="panel"><Empty title={(q.error as ApiError).status === 403 ? 'You can’t open this incident' : 'Incident not found'}>
      {(q.error as Error).message} <Link to="/" className="text-action font-semibold">Back to your incidents</Link>
    </Empty></div>
  )
  const d = q.data!
  const s = d.summary
  const can = (a: string) => d.allowedActions.includes(a)
  const act = (path: string, msg: string, body?: unknown) => run.mutate({ path, body }, { onSuccess: () => toast.success(msg) })

  return (
    <div className="space-y-5">
      <Link to={staff ? '/incidents' : '/my'} className="inline-flex items-center gap-1.5 text-[14px] font-semibold text-ink-3 hover:text-ink"><ArrowLeft className="size-4" />Back to incidents</Link>

      {/* ---------- header ---------- */}
      <header className="panel p-5 sm:p-6">
        <div className="flex flex-wrap items-center gap-2.5">
          <PriorityTag priority={s.priority} size="lg" withLabel />
          <span className="font-display text-[34px] leading-none font-extrabold text-ink tabular-nums">{s.number}</span>
          <StatusBadge status={s.status} />
          {s.escalationLevel !== 'NONE' && d.summary.status !== 'CLOSED' && <span className="chip bg-sunken text-ink-2 border-line-strong"><Siren className="size-3.5" />Escalated to {titleCase(s.escalationLevel)}</span>}
        </div>
        <h1 className="font-display text-[28px] sm:text-[32px] font-bold mt-3 leading-[1.08]">{s.title}</h1>
        <p className="text-ink-3 text-[14px] mt-1.5">
          {s.service?.name} · {s.category?.name}{d.autoCategorized && d.categoryConfidence != null ? ` (auto, ${Math.round(d.categoryConfidence * 100)}%)` : ''} · reported by {d.reporter.name} {ago(s.createdAt)}
        </p>
        <Lifecycle status={s.status} />
        <ActionBar d={d} can={can} busy={run.isPending} open={setDialog} act={act} />
      </header>

      {d.duplicateOf && (
        <div className="panel px-4 py-3 flex items-center gap-3 text-[13.5px]">
          <GitMerge className="size-4 text-ink-3 shrink-0" />
          <span>Linked to <Link className="font-semibold text-action hover:underline" to={`/incidents/${d.duplicateOf.id}`}>{d.duplicateOf.number} · {d.duplicateOf.title}</Link>
            {d.duplicateOf.score != null && <span className="text-ink-3"> ({Math.round(d.duplicateOf.score * 100)}% similar)</span>}. Both are handled together.</span>
        </div>
      )}

      {user.role === 'EMPLOYEE' && s.status === 'RESOLVED' && can('FEEDBACK') && (
        <div className="panel p-5 flex flex-wrap items-center gap-4 border-ok/40 bg-[#e3f1e8]/60">
          <CircleCheck className="size-6 text-ok" />
          <div className="flex-1 min-w-[220px]"><div className="font-bold">Is it fixed?</div><div className="text-ink-2 text-[13.5px]">{d.assignee?.name ?? 'IT'} marked this resolved. Confirm to close it, or reopen it if the problem is still there.</div></div>
          <button className="btn-secondary" onClick={() => setDialog('REOPEN')}>Still broken</button>
          <button className="btn-primary" onClick={() => setDialog('FEEDBACK')}><Check className="size-4" />Yes, it's fixed</button>
        </div>
      )}
      {user.role === 'EMPLOYEE' && s.status === 'AWAITING_INFO' && (
        <div className="panel p-5 flex flex-wrap items-center gap-4 border-[#553a8f]/30 bg-[#efeaf7]/60">
          <MessageSquare className="size-6 text-[#553a8f]" />
          <div className="flex-1 min-w-[220px]"><div className="font-bold">{d.assignee?.name ?? 'Your technician'} needs more information</div>
            <div className="text-ink-2 text-[13.5px]">{[...d.comments].reverse().find(c => c.type === 'INFO_REQUEST')?.body}</div></div>
          <button className="btn-primary" onClick={() => setDialog('PROVIDE_INFO')}>Reply</button>
        </div>
      )}

      <div className="grid xl:grid-cols-[minmax(0,1fr)_380px] gap-5 items-start">
        <div className="space-y-5 min-w-0">
          <SlaClock d={d} />
          <section className="panel">
            <div className="panel-head"><span className="panel-title">Description</span></div>
            <p className="p-4 whitespace-pre-wrap leading-relaxed text-ink-2 max-w-[75ch]">{d.description}</p>
          </section>
          {d.resolution && (
            <section className="panel">
              <div className="panel-head"><span className="panel-title">Resolution</span>{d.resolvedAt && <span className="text-[12.5px] text-ink-3">{fmtDateTime(d.resolvedAt)}</span>}</div>
              <div className="p-4 space-y-2 max-w-[75ch]">
                <p className="leading-relaxed">{d.resolution}</p>
                {d.rootCause && <p className="text-[13.5px] text-ink-3"><span className="font-semibold text-ink-2">Root cause:</span> {d.rootCause}</p>}
                {d.feedbackRating != null && (
                  <p className="flex items-center gap-2 text-[13.5px] pt-1"><Stars value={d.feedbackRating} />{d.feedbackComment && <span className="text-ink-2">“{d.feedbackComment}”</span>}</p>
                )}
                {d.closureNotes && <p className="text-[13.5px] text-ink-3">{d.closureNotes}</p>}
              </div>
            </section>
          )}
          <Conversation d={d} staff={staff} />
        </div>

        <aside className="space-y-5 min-w-0">
          <Facts d={d} />
          {staff && <Recommendations id={id} d={d} onAssign={tid => act('assign', 'Technician assigned', { technicianId: tid })} canAssign={can('ASSIGN')} />}
          {staff && d.summary.status !== 'CLOSED' && <SuggestedFixes id={id} />}
          {staff && can('MERGE') && <SimilarOpen id={id} createdAt={s.createdAt} onMerge={rootId => act('merge', 'Merged as duplicate', { duplicateOfId: rootId })} />}
          <Timeline items={timeline.data} loading={timeline.isLoading} />
        </aside>
      </div>

      <ActionDialogs key={dialog ?? "none"} d={d} which={dialog} close={() => setDialog(null)} busy={run.isPending} error={run.error} run={(path, body, msg) => act(path, msg, body)} />
    </div>
  )
}

/* ================================================================================================ */

function Lifecycle({ status }: { status: Status }) {
  if (status === 'CANCELLED') return <p className="mt-5 text-[14px] text-ink-3">This incident was cancelled.</p>
  const shown = status === 'AWAITING_INFO' ? 'IN_PROGRESS' : status
  const idx = LIFECYCLE.indexOf(shown)
  return (
    <ol className="mt-5 grid grid-cols-4 sm:grid-cols-8 gap-1.5" aria-label="Lifecycle">
      {LIFECYCLE.map((st, i) => (
        <li key={st} className="min-w-0" aria-current={i === idx ? 'step' : undefined}>
          <span className={cx('block h-[6px] rounded-[1px]', i < idx ? 'bg-board-2' : i === idx ? 'bg-ink' : 'bg-sunken border border-line')} />
          <span className={cx('block mt-1.5 text-[13px] truncate', i === idx ? 'font-bold text-ink' : i < idx ? 'text-ink-2' : 'text-ink-3')}>
            {st === 'IN_PROGRESS' && status === 'AWAITING_INFO' ? 'Waiting on reporter' : STATUS_LABEL[st]}
          </span>
        </li>
      ))}
    </ol>
  )
}

function ActionBar({ d, can, busy, open, act }: { d: Detail; can: (a: string) => boolean; busy: boolean; open: (a: ActionKey) => void; act: (path: string, msg: string) => void }) {
  const user = useUser()
  const primary: ReactNode[] = []
  if (can('TAKE')) primary.push(<button key="take" className="btn-primary" disabled={busy} onClick={() => act('take', 'Assigned to you')}><UserPlus className="size-4" />Take this incident</button>)
  if (can('ACKNOWLEDGE')) primary.push(<button key="ack" className="btn-primary" disabled={busy} onClick={() => act('acknowledge', 'Acknowledged; response time recorded')}>Acknowledge</button>)
  if (can('START')) primary.push(<button key="start" className="btn-primary" disabled={busy} onClick={() => act('start', 'Investigation started')}>Start investigation</button>)
  if (can('RESOLVE')) primary.push(<button key="res" className="btn-primary" onClick={() => open('RESOLVE')}><Check className="size-4" />Resolve</button>)
  if (can('REQUEST_INFO')) primary.push(<button key="ri" className="btn-secondary" onClick={() => open('REQUEST_INFO')}>Ask reporter for info</button>)
  if (can('PROVIDE_INFO') && user.role !== 'EMPLOYEE') primary.push(<button key="pi" className="btn-secondary" onClick={() => open('PROVIDE_INFO')}>Add reporter's answer</button>)
  if (can('VERIFY') && user.role !== 'EMPLOYEE') primary.push(<button key="ver" className="btn-secondary" disabled={busy} onClick={() => act('verify', 'Resolution verified')}>Verify</button>)
  if (can('CLOSE')) primary.push(<button key="close" className={d.summary.status === 'VERIFIED' ? 'btn-primary' : 'btn-secondary'} onClick={() => open('CLOSE')}>Close incident</button>)
  if (can('ASSIGN')) primary.push(<button key="asg" className="btn-secondary" onClick={() => open('ASSIGN')}><Users className="size-4" />{d.assignee ? 'Reassign' : 'Assign'}</button>)
  if (can('REASSESS') || can('EDIT')) primary.push(<button key="edit" className="btn-ghost" onClick={() => open('EDIT')}><Pencil className="size-4" />{can('REASSESS') ? 'Reassess' : 'Edit'}</button>)
  if (can('REOPEN') && user.role !== 'EMPLOYEE') primary.push(<button key="re" className="btn-ghost" onClick={() => open('REOPEN')}>Reopen</button>)

  const destructive: ReactNode[] = []
  if (can('MERGE')) destructive.push(<button key="mg" className="btn-ghost btn-sm" onClick={() => open('MERGE')}><GitMerge className="size-4" />Merge duplicate</button>)
  if (can('CANCEL')) destructive.push(<button key="cx" className="btn-danger btn-sm" onClick={() => open('CANCEL')}>Cancel incident</button>)

  if (!primary.length && !destructive.length) return null
  return (
    <div className="mt-5 pt-4 border-t border-line flex flex-wrap items-center gap-2">
      {primary}
      {busy && <Spinner className="size-4 text-ink-3" />}
      {destructive.length > 0 && <div className="ml-auto flex items-center gap-2 pl-6">{destructive}</div>}
    </div>
  )
}

/** The SLA clock: both targets drawn as exact durations on one ruler, with a live "now" marker. */
function SlaClock({ d }: { d: Detail }) {
  const now = useNow()
  const s = d.summary
  if (!s.resolutionDueAt) return null
  const start = new Date(s.createdAt).getTime()
  const resp = new Date(s.responseDueAt!).getTime()
  const reso = new Date(s.resolutionDueAt).getTime()
  const open = !['RESOLVED', 'VERIFIED', 'CLOSED', 'CANCELLED'].includes(s.status)
  const end = open ? now : new Date(d.resolvedAt ?? d.closedAt ?? s.updatedAt ?? now).getTime()
  const span = Math.max(reso, end) - start
  const pct = (t: number) => `${Math.min(100, Math.max(0, ((t - start) / span) * 100))}%`
  const paused = s.status === 'AWAITING_INFO'
  const ackAt = d.acknowledgedAt ? new Date(d.acknowledgedAt).getTime() : null

  const Row = ({ label, due, doneAt, breached, target }: { label: string; due: number; doneAt: number | null; breached: boolean; target: number }) => {
    const left = due - now
    const state = doneAt ? (doneAt <= due ? 'met' : 'late') : breached || left < 0 ? 'breached' : paused && label === 'Resolution' ? 'paused' : left < (due - start) * 0.25 ? 'risk' : 'ok'
    const bar = { ok: 'bg-plate', risk: 'bg-p3', breached: 'bg-p1', met: 'bg-[#8fd3ad]', late: 'bg-p1', paused: 'bg-chalk/40' }[state]
    const urgent = label === 'Resolution' && !doneAt && state !== 'paused'
    return (
      <div className="grid grid-cols-[minmax(0,1fr)_auto] sm:grid-cols-[120px_1fr_auto] items-center gap-x-4 gap-y-2">
        <div><div className="text-[14px] font-semibold text-white">{label}</div><div className="text-[12.5px] text-chalk">target {minutes(target)}</div></div>
        <div className="relative h-7 col-span-2 sm:col-span-1 order-last sm:order-none">
          <div className="absolute inset-x-0 top-1/2 -translate-y-1/2 h-[12px] rounded-[1px] bg-board-2" />
          <div className={cx('absolute top-1/2 -translate-y-1/2 h-[12px] rounded-[1px]', bar)} style={{ left: 0, width: pct(doneAt ?? Math.min(now, due)) }} />
          {!doneAt && now > due && <div className="absolute top-1/2 -translate-y-1/2 h-[12px] bg-p1/60" style={{ left: pct(due), width: `calc(${pct(now)} - ${pct(due)})` }} />}
          <div className="absolute top-0 bottom-0 w-[2px] bg-white" style={{ left: pct(due) }} title={`Due ${fmtDateTime(new Date(due).toISOString())}`} />
        </div>
        <div className={cx('plate text-right whitespace-nowrap min-w-[112px] sm:w-[150px] px-3 pt-3.5 pb-1 font-display font-extrabold text-[24px] sm:text-[28px] leading-none tabular-nums', urgent && 'plate-reversed', urgent ? { ok: 'text-white', risk: 'text-[#ffd36b]', breached: 'text-[#ff9c92]', met: '', late: '', paused: '' }[state] : { ok: 'text-ink', risk: 'text-warn', breached: 'text-bad', met: 'text-ok', late: 'text-bad', paused: 'text-ink-3' }[state])}>
          {state === 'met' ? 'Met' : state === 'late' ? 'Missed' : state === 'paused' ? 'Paused' : left < 0 ? `${countdown(-left)} late` : `${countdown(left)} left`}
        </div>
      </div>
    )
  }

  return (
    <section className="bg-board rounded-[6px]" aria-label="SLA clock">
      <div className="flex flex-wrap items-center justify-between gap-2 px-4 h-12 border-b border-board-line">
        <span className="font-display text-[18px] font-bold text-white">SLA clock</span>
        <span className="text-[13px] text-chalk">{d.slaPolicyName} policy · {s.priority} {s.priority && PRIORITY_LABEL[s.priority]}{paused ? ' · paused while waiting on reporter' : ''}</span>
      </div>
      <div className="p-4 space-y-3.5">
        <Row label="First response" due={resp} doneAt={ackAt} breached={d.responseBreached} target={d.responseTargetMinutes} />
        <Row label="Resolution" due={reso} doneAt={d.resolvedAt ? new Date(d.resolvedAt).getTime() : null} breached={d.resolutionBreached} target={d.resolutionTargetMinutes} />
        <div className="hidden sm:grid grid-cols-[120px_1fr_auto] gap-4 text-[13px] text-chalk tnum">
          <span />
          <span className="flex justify-between"><span>{fmtTime(s.createdAt)} opened</span><span>due {fmtTime(s.resolutionDueAt)}</span></span>
          <span className="w-[112px] sm:w-[150px]" />
        </div>
      </div>
    </section>
  )
}

function Facts({ d }: { d: Detail }) {
  const row = (k: string, v: ReactNode) => <div className="grid grid-cols-[120px_1fr] gap-3 py-2 border-b border-line last:border-0"><dt className="text-ink-3 text-[13px]">{k}</dt><dd className="text-[13.5px] font-semibold min-w-0">{v}</dd></div>
  return (
    <section className="panel">
      <div className="panel-head"><span className="panel-title">Details</span></div>
      <dl className="px-4 py-1">
        {row('Technician', d.assignee ? <>{d.assignee.name}<span className="block text-ink-3 font-normal text-[12.5px]">{d.assignee.email}</span></> : <span className="text-warn">Not assigned yet</span>)}
        {row('Reporter', <>{d.reporter.name}<span className="block text-ink-3 font-normal text-[12.5px]">{d.reporter.department ?? d.reporter.email}</span></>)}
        {row('Impact', titleCase(d.impact))}
        {row('Urgency', titleCase(d.urgency))}
        {row('Affected users', <span className="mono">{d.affectedUsers.toLocaleString()}</span>)}
        {row('Priority score', <><span className="mono">{d.priorityScore}</span>{d.priorityReason && <span className="block text-ink-3 font-normal text-[12.5px] leading-snug mt-0.5">{d.priorityReason}</span>}</>)}
        {row('Opened', fmtDateTime(d.summary.createdAt))}
        {d.acknowledgedAt && row('Acknowledged', fmtDateTime(d.acknowledgedAt))}
        {d.closedAt && row('Closed', fmtDateTime(d.closedAt))}
      </dl>
    </section>
  )
}

function Conversation({ d, staff }: { d: Detail; staff: boolean }) {
  const qc = useQueryClient()
  const [text, setText] = useState('')
  const [internal, setInternal] = useState(false)
  const send = useMutation({
    mutationFn: () => post<Detail>(`/incidents/${d.summary.id}/comments`, { text, internal }),
    onSuccess: n => { qc.setQueryData(['incident', d.summary.id], n); qc.invalidateQueries({ queryKey: ['incident', d.summary.id, 'timeline'] }); setText('') },
    onError: e => toast.error((e as Error).message),
  })
  const canWrite = d.allowedActions.includes('COMMENT')
  const LABEL: Record<string, string> = { INFO_REQUEST: 'asked for information', INFO_RESPONSE: 'replied', RESOLUTION: 'posted the resolution', INVESTIGATION_NOTE: 'internal note' }
  return (
    <section className="panel">
      <div className="panel-head"><span className="panel-title">Conversation</span><span className="text-[12.5px] text-ink-3">{d.comments.length} message{d.comments.length === 1 ? '' : 's'}</span></div>
      {d.comments.length === 0 ? (
        <p className="px-4 py-6 text-[13.5px] text-ink-3">No messages yet. {staff ? 'Updates you post here are visible to the reporter unless marked internal.' : 'Add anything that might help the technician.'}</p>
      ) : (
        <ol className="divide-y divide-line">
          {d.comments.map(c => (
            <li key={c.id} className={cx('px-4 py-3.5', c.internal && 'bg-p3-soft/50')}>
              <div className="flex items-center gap-2 text-[13px]">
                <span className="font-bold text-ink">{c.author}</span>
                <span className="text-ink-3">{c.authorRole === 'EMPLOYEE' ? 'Reporter' : c.authorRole === 'TECHNICIAN' ? 'Technician' : 'Admin'}</span>
                {LABEL[c.type] && <span className={cx('chip h-5', c.internal ? 'bg-p3-soft text-[#7a5303] border-p3/30' : 'bg-sunken text-ink-2 border-line')}>{c.internal && <Lock className="size-3" />}{LABEL[c.type]}</span>}
                <span className="ml-auto text-ink-3">{fmtDateTime(c.createdAt)}</span>
              </div>
              <p className="mt-1 whitespace-pre-wrap leading-relaxed text-ink-2 max-w-[75ch]">{c.body}</p>
            </li>
          ))}
        </ol>
      )}
      {canWrite && (
        <form className="border-t border-line p-4 space-y-3" onSubmit={e => { e.preventDefault(); if (text.trim()) send.mutate() }}>
          <textarea className="input" rows={3} placeholder={internal ? 'Investigation note (only IT staff can see this)…' : 'Write a message…'} value={text} onChange={e => setText(e.target.value)} aria-label="Message" />
          <div className="flex items-center gap-3">
            {staff && d.allowedActions.includes('NOTE') && (
              <label className="inline-flex items-center gap-2 text-[13.5px] font-semibold text-ink-2 cursor-pointer">
                <input type="checkbox" className="size-4 accent-[var(--color-p3)]" checked={internal} onChange={e => setInternal(e.target.checked)} />Internal note
              </label>
            )}
            <button className="btn-primary ml-auto" disabled={!text.trim() || send.isPending}>{send.isPending ? <Spinner /> : internal ? 'Add note' : 'Send'}</button>
          </div>
        </form>
      )}
    </section>
  )
}

function Recommendations({ id, d, onAssign, canAssign }: { id: number; d: Detail; onAssign: (tid: number) => void; canAssign: boolean }) {
  const open = !['RESOLVED', 'VERIFIED', 'CLOSED', 'CANCELLED'].includes(d.summary.status)
  const q = useQuery({ queryKey: ['incident', id, 'recs'], queryFn: () => api<Recommendation[]>(`/incidents/${id}/recommendations`), enabled: open })
  if (!open) return null
  return (
    <section className="panel">
      <div className="panel-head"><span className="panel-title flex items-center gap-2"><Users className="size-4 text-action" />Best-fit technicians</span></div>
      {q.isLoading ? <div className="p-4 space-y-2"><Skeleton className="h-10" /><Skeleton className="h-10" /></div> : !q.data?.length ? (
        <p className="p-4 text-[13.5px] text-ink-3">Nobody else has spare capacity right now.</p>
      ) : (
        <ul className="divide-y divide-line">
          {q.data.slice(0, 4).map((r, i) => (
            <li key={r.technicianId} className="px-4 py-3 space-y-1.5">
              <div className="flex items-center gap-2">
                <span className="font-bold text-[13.5px]">{r.name}</span>
                {i === 0 && <span className="chip h-5 bg-action-soft text-action border-action/25">Top match</span>}
                <span className="ml-auto mono text-[15px] font-semibold text-ink-2">{Math.round(r.score * 100)}</span>
                {canAssign && <button className="btn-secondary btn-sm h-7" onClick={() => onAssign(r.technicianId)}>Assign</button>}
              </div>
              <div className="text-[12.5px] text-ink-3">{r.skills.join(', ')} · {r.reason}</div>
              <CapacityBar value={r.activeLoad} max={r.maxWorkload} />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function SuggestedFixes({ id }: { id: number }) {
  const q = useQuery({ queryKey: ['incident', id, 'fixes'], queryFn: () => api<Similar[]>(`/incidents/${id}/suggested-resolutions`) })
  if (!q.data?.length) return null
  return (
    <section className="panel">
      <div className="panel-head"><span className="panel-title flex items-center gap-2"><Lightbulb className="size-4 text-p3" />Suggested resolutions</span></div>
      <ul className="divide-y divide-line">
        {q.data.map(s => (
          <li key={s.id} className="px-4 py-3">
            <div className="flex items-center gap-2 text-[12.5px]"><Link to={`/incidents/${s.id}`} className="mono text-ink-3 hover:text-action">{s.number}</Link><span className="ml-auto mono text-ink-3">{Math.round(s.score * 100)}% similar</span></div>
            <div className="text-[13.5px] font-semibold mt-0.5">{s.title}</div>
            <p className="text-[13px] text-ink-2 mt-1 flex gap-1.5"><CornerDownRight className="size-3.5 mt-0.5 shrink-0 text-ink-3" />{s.resolution}</p>
          </li>
        ))}
      </ul>
    </section>
  )
}

function SimilarOpen({ id, createdAt, onMerge }: { id: number; createdAt: string; onMerge: (rootId: number) => void }) {
  const q = useQuery({ queryKey: ['incident', id, 'similar'], queryFn: () => api<Similar[]>(`/incidents/${id}/similar`) })
  if (!q.data?.length) return null
  return (
    <section className="panel">
      <div className="panel-head"><span className="panel-title flex items-center gap-2"><GitMerge className="size-4 text-warn" />Possibly the same problem</span></div>
      <ul className="divide-y divide-line">
        {q.data.map(s => (
          <li key={s.id} className="px-4 py-3">
            <div className="flex items-center gap-2 text-[12.5px]"><Link to={`/incidents/${s.id}`} className="mono text-ink-3 hover:text-action">{s.number}</Link><StatusBadge status={s.status} /><span className="ml-auto mono text-ink-3">{Math.round(s.score * 100)}%</span></div>
            <div className="text-[13.5px] font-semibold mt-1">{s.title}</div>
            {s.createdAt < createdAt
              ? <button className="btn-ghost btn-sm mt-1 -ml-2" onClick={() => onMerge(s.id)}>Mark this a duplicate of {s.number}</button>
              : <span className="block text-[12.5px] text-ink-3 mt-1">Reported after this one</span>}
          </li>
        ))}
      </ul>
    </section>
  )
}

function Timeline({ items, loading }: { items?: AuditView[]; loading: boolean }) {
  return (
    <section className="panel">
      <div className="panel-head"><span className="panel-title">Audit trail</span><span className="text-[12.5px] text-ink-3">{items?.length ?? 0} events</span></div>
      {loading ? <div className="p-4 space-y-2"><Skeleton className="h-8" /><Skeleton className="h-8" /><Skeleton className="h-8" /></div> : (
        <ol className="px-4 py-3 max-h-[440px] overflow-y-auto">
          {items?.map(a => (
            <li key={a.id} className="relative pl-5 pb-3 last:pb-0 before:absolute before:left-[4px] before:top-2 before:bottom-0 before:w-px before:bg-line last:before:hidden">
              <span className={cx('absolute left-0 top-[6px] size-[9px] rounded-full border-2 border-surface', /BREACH|ESCALAT|MAJOR/.test(a.action) ? 'bg-p1' : a.toState && a.fromState !== a.toState && a.fromState ? 'bg-action' : 'bg-line-strong')} />
              <div className="flex items-baseline gap-2 text-[12px]"><span className="mono text-ink-3">{fmtTime(a.timestamp)}</span><span className="font-semibold text-ink-2">{a.actor}</span></div>
              <p className="text-[13px] text-ink leading-snug">{a.description}</p>
              {a.fromState && a.toState && a.fromState !== a.toState && <p className="text-[12px] text-ink-3 mono">{a.fromState} → {a.toState}</p>}
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

function Stars({ value, onChange }: { value: number; onChange?: (v: number) => void }) {
  return (
    <span className="inline-flex gap-0.5" role={onChange ? 'radiogroup' : undefined} aria-label={`${value} out of 5`}>
      {[1, 2, 3, 4, 5].map(n => onChange ? (
        <button key={n} type="button" role="radio" aria-checked={value === n} aria-label={`${n} star${n > 1 ? 's' : ''}`} onClick={() => onChange(n)} className="p-0.5">
          <Star className={cx('size-7 transition-colors', n <= value ? 'fill-p3 text-p3' : 'text-line-strong hover:text-p3')} />
        </button>
      ) : <Star key={n} className={cx('size-4', n <= value ? 'fill-p3 text-p3' : 'text-line-strong')} />)}
    </span>
  )
}

/* ---------------------------------- dialogs ---------------------------------- */

function ActionDialogs({ d, which, close, busy, error, run }: {
  d: Detail; which: ActionKey | null; close: () => void; busy: boolean; error: unknown; run: (path: string, body: unknown, msg: string) => void
}) {
  const [text, setText] = useState('')
  const [root, setRoot] = useState('')
  const [rating, setRating] = useState(5)
  const [tech, setTech] = useState('')
  const [edit, setEdit] = useState({ title: d.summary.title, description: d.description, impact: d.impact, urgency: d.urgency, affectedUsers: d.affectedUsers })
  const qc = useQueryClient()
  const technicians = useQuery({ queryKey: ['technicians'], queryFn: () => api<UserView[]>('/admin/users?role=TECHNICIAN'), enabled: which === 'ASSIGN' })
  const save = useMutation({
    mutationFn: () => {
      const body: Record<string, unknown> = {}
      if (d.allowedActions.includes('EDIT')) { if (edit.title !== d.summary.title) body.title = edit.title; if (edit.description !== d.description) body.description = edit.description }
      if (d.allowedActions.includes('REASSESS')) { if (edit.impact !== d.impact) body.impact = edit.impact; if (edit.urgency !== d.urgency) body.urgency = edit.urgency; if (edit.affectedUsers !== d.affectedUsers) body.affectedUsers = edit.affectedUsers }
      return patch<Detail>(`/incidents/${d.summary.id}`, body)
    },
    onSuccess: n => { qc.setQueryData(['incident', d.summary.id], n); qc.invalidateQueries({ queryKey: ['incident', d.summary.id, 'timeline'] }); toast.success('Incident updated'); close() },
  })
  const fields = error instanceof ApiError ? error.fields ?? {} : {}
  const reset = () => { setText(''); setRoot(''); setRating(5); setTech(''); close() }
  const submit = (path: string, body: unknown, msg: string) => (e: React.FormEvent) => { e.preventDefault(); run(path, body, msg) }
  const textField = (label: string, hint?: string, rows = 4) => (
    <Field label={label} error={fields.text} hint={hint}><textarea className="input" rows={rows} value={text} onChange={e => setText(e.target.value)} autoFocus /></Field>
  )
  const footer = (label: string, danger?: boolean, disabled?: boolean) => (
    <div className="flex justify-end gap-2 pt-2">
      <button type="button" className="btn-ghost" onClick={reset}>Back</button>
      <button className={danger ? 'btn-danger' : 'btn-primary'} disabled={busy || disabled}>{busy ? <Spinner /> : label}</button>
    </div>
  )
  const err = error && !Object.keys(fields).length ? <ErrorNote error={error} /> : null

  return (
    <>
      <Dialog open={which === 'REQUEST_INFO'} onClose={reset} title="Ask the reporter for information">
        <form className="space-y-4" onSubmit={submit('request-info', { text }, 'Question sent; SLA clock paused')}>
          {textField('Your question', 'The resolution SLA pauses until they reply.')}{err}{footer('Send question', false, !text.trim())}
        </form>
      </Dialog>
      <Dialog open={which === 'PROVIDE_INFO'} onClose={reset} title="Reply to the technician">
        <form className="space-y-4" onSubmit={submit('provide-info', { text }, 'Reply sent; work resumes')}>
          {textField('Your answer')}{err}{footer('Send reply', false, !text.trim())}
        </form>
      </Dialog>
      <Dialog open={which === 'RESOLVE'} onClose={reset} title={`Resolve ${d.summary.number}`}>
        <form className="space-y-4" onSubmit={submit('resolve', { resolution: text, rootCause: root || null }, 'Resolved; reporter notified')}>
          <Field label="What fixed it?" error={fields.resolution} hint="The reporter sees this, and it becomes a suggested fix for similar incidents.">
            <textarea className="input" rows={4} value={text} onChange={e => setText(e.target.value)} autoFocus />
          </Field>
          <Field label="Root cause (optional)" error={fields.rootCause}><input className="input" value={root} onChange={e => setRoot(e.target.value)} /></Field>
          {err}{footer('Mark resolved', false, text.trim().length < 5)}
        </form>
      </Dialog>
      <Dialog open={which === 'REOPEN'} onClose={reset} title="Reopen this incident">
        <form className="space-y-4" onSubmit={submit('reopen', { text }, 'Reopened; technician notified')}>
          {textField('What is still wrong?', undefined, 3)}{err}{footer('Reopen', false, !text.trim())}
        </form>
      </Dialog>
      <Dialog open={which === 'CLOSE'} onClose={reset} title={`Close ${d.summary.number}`}>
        <form className="space-y-4" onSubmit={submit('close', { text: text || null }, 'Incident closed')}>
          {textField('Closure notes (optional)', undefined, 3)}{err}{footer('Close incident')}
        </form>
      </Dialog>
      <Dialog open={which === 'CANCEL'} onClose={reset} title="Cancel this incident">
        <form className="space-y-4" onSubmit={submit('cancel', { text }, 'Incident cancelled')}>
          <p className="text-[13.5px] text-ink-2">Cancelled incidents leave every queue and can't be reopened. The reason is kept in the audit trail.</p>
          {textField('Reason', undefined, 3)}{err}{footer('Cancel incident', true, !text.trim())}
        </form>
      </Dialog>
      <Dialog open={which === 'MERGE'} onClose={reset} title="Merge as a duplicate">
        <form className="space-y-4" onSubmit={e => { e.preventDefault(); const n = Number(root.replace(/\D/g, '')); run('merge', { duplicateOfId: n >= 1000 ? n - 1000 : n }, 'Merged as duplicate') }}>
          <p className="text-[13.5px] text-ink-2">This incident will be cancelled and linked to the original, which keeps all the work.</p>
          <Field label="Original incident number" hint="e.g. INC1105"><input className="input mono" value={root} onChange={e => setRoot(e.target.value)} autoFocus /></Field>
          {err}{footer('Merge', true, !/\d+/.test(root))}
        </form>
      </Dialog>
      <Dialog open={which === 'FEEDBACK'} onClose={reset} title="How did we do?">
        <form className="space-y-4" onSubmit={submit('feedback', { rating, comment: text || null }, 'Thanks! Incident closed')}>
          <div><span className="field-label">Rate the fix</span><Stars value={rating} onChange={setRating} /></div>
          {textField('Anything to add? (optional)', undefined, 3)}{err}{footer('Confirm fix & close')}
        </form>
      </Dialog>
      <Dialog open={which === 'ASSIGN'} onClose={reset} title={`Assign ${d.summary.number}`}>
        <form className="space-y-4" onSubmit={submit('assign', { technicianId: tech ? Number(tech) : null }, 'Technician assigned')}>
          <Field label="Technician" hint="Leave on automatic to let the active assignment strategy pick.">
            <select className="input" value={tech} onChange={e => setTech(e.target.value)}>
              <option value="">Automatic (active strategy)</option>
              {technicians.data?.filter(t => t.active).map(t => (
                <option key={t.id} value={t.id} disabled={!t.available}>{t.fullName} · {t.activeLoad}/{t.maxWorkload} active · {t.skills?.map(s => s.name).join(', ')}{t.available ? '' : ' (unavailable)'}</option>
              ))}
            </select>
          </Field>
          {err}{footer('Assign')}
        </form>
      </Dialog>
      <Dialog open={which === 'EDIT'} onClose={() => { save.reset(); close() }} title="Update incident" wide>
        <form className="space-y-4" onSubmit={e => { e.preventDefault(); save.mutate() }}>
          {d.allowedActions.includes('EDIT') && <>
            <Field label="Summary"><input className="input" value={edit.title} onChange={e => setEdit(s => ({ ...s, title: e.target.value }))} /></Field>
            <Field label="Description"><textarea className="input" rows={4} value={edit.description} onChange={e => setEdit(s => ({ ...s, description: e.target.value }))} /></Field>
          </>}
          {d.allowedActions.includes('REASSESS') && (
            <div className="grid sm:grid-cols-3 gap-3">
              <Field label="Impact"><select className="input" value={edit.impact} onChange={e => setEdit(s => ({ ...s, impact: e.target.value as Impact }))}>{['LOW', 'MEDIUM', 'HIGH', 'EXTENSIVE'].map(v => <option key={v} value={v}>{titleCase(v)}</option>)}</select></Field>
              <Field label="Urgency"><select className="input" value={edit.urgency} onChange={e => setEdit(s => ({ ...s, urgency: e.target.value as Urgency }))}>{['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(v => <option key={v} value={v}>{titleCase(v)}</option>)}</select></Field>
              <Field label="Affected users"><input className="input mono" type="number" min={1} value={edit.affectedUsers} onChange={e => setEdit(s => ({ ...s, affectedUsers: Number(e.target.value) }))} /></Field>
            </div>
          )}
          {d.allowedActions.includes('REASSESS') && <p className="text-[13px] text-ink-3">Changing impact, urgency or users recalculates priority and SLA deadlines.</p>}
          <ErrorNote error={save.error} />
          <div className="flex justify-end gap-2"><button type="button" className="btn-ghost" onClick={close}>Back</button><button className="btn-primary" disabled={save.isPending}>{save.isPending ? <Spinner /> : 'Save changes'}</button></div>
        </form>
      </Dialog>
    </>
  )
}

function DetailSkeleton() {
  return (
    <div className="space-y-5">
      <Skeleton className="h-5 w-32" />
      <div className="panel p-6 space-y-3"><Skeleton className="h-8 w-60" /><Skeleton className="h-7 w-3/4" /><Skeleton className="h-4 w-1/2" /><Skeleton className="h-10 w-full mt-4" /></div>
      <div className="grid xl:grid-cols-[1fr_380px] gap-5"><Skeleton className="h-64" /><Skeleton className="h-64" /></div>
    </div>
  )
}
