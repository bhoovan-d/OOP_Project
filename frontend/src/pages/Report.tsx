import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Lightbulb, Sparkles, TriangleAlert } from 'lucide-react'
import { ApiError, api, post, type Analysis, type Catalog, type Detail, type Impact, type Urgency } from '../lib/api'
import { minutes, PRIORITY_LABEL } from '../lib/format'
import { ErrorNote, Field, PageHeader, Skeleton, Spinner, StatusBadge, TriageTag, cx } from '../components/ui'

const IMPACT: { v: Impact; label: string; hint: string }[] = [
  { v: 'LOW', label: 'Just me', hint: 'One person affected' },
  { v: 'MEDIUM', label: 'My team', hint: 'A team or department' },
  { v: 'HIGH', label: 'Several teams', hint: 'Multiple departments' },
  { v: 'EXTENSIVE', label: 'Everyone', hint: 'Organisation or customers' },
]
const URGENCY: { v: Urgency; label: string; hint: string }[] = [
  { v: 'LOW', label: 'Can wait', hint: 'Workaround exists' },
  { v: 'MEDIUM', label: 'Slowing work', hint: 'Degraded, still working' },
  { v: 'HIGH', label: 'Blocking work', hint: 'Cannot do my job' },
  { v: 'CRITICAL', label: 'Business down', hint: 'Revenue or safety at stake' },
]

function useDebounced<T>(value: T, ms = 450) {
  const [v, setV] = useState(value)
  useEffect(() => { const t = setTimeout(() => setV(value), ms); return () => clearTimeout(t) }, [value, ms])
  return v
}

export default function Report() {
  const nav = useNavigate()
  const catalog = useQuery({ queryKey: ['catalog'], queryFn: () => api<Catalog>('/catalog'), staleTime: 300_000 })
  const [f, setF] = useState({ title: '', description: '', serviceId: '', categoryId: '', impact: 'MEDIUM' as Impact, urgency: 'MEDIUM' as Urgency, affectedUsers: 1 })
  const set = <K extends keyof typeof f>(k: K, v: (typeof f)[K]) => setF(s => ({ ...s, [k]: v }))

  const probe = useDebounced({ title: f.title, description: f.description, serviceId: f.serviceId, impact: f.impact, urgency: f.urgency, affectedUsers: f.affectedUsers })
  const analysis = useQuery({
    queryKey: ['analyze', probe],
    queryFn: () => post<Analysis>('/insights/analyze', { ...probe, serviceId: probe.serviceId ? Number(probe.serviceId) : null }),
    enabled: (probe.title + probe.description).length >= 4 || !!probe.serviceId,
    placeholderData: prev => prev,
  })

  const create = useMutation({
    mutationFn: () => post<Detail>('/incidents', {
      title: f.title, description: f.description, serviceId: f.serviceId ? Number(f.serviceId) : null,
      categoryId: f.categoryId ? Number(f.categoryId) : null, impact: f.impact, urgency: f.urgency, affectedUsers: Number(f.affectedUsers) || 1,
    }),
    onSuccess: d => {
      const s = d.summary
      toast.success(`${s.number} logged as ${s.priority}`, { description: s.assignee ? `Assigned to ${s.assignee.name}.` : 'Queued for the next available technician.' })
      nav(`/incidents/${s.id}`)
    },
  })
  const fields = create.error instanceof ApiError ? create.error.fields ?? {} : {}
  const a = analysis.data
  const topCat = a?.categories[0]

  return (
    <>
      <PageHeader title="Report an incident" sub="Describe what's wrong. I-ITSM classifies, prioritises and routes it as you type." />
      <div className="grid xl:grid-cols-[minmax(0,1fr)_400px] gap-6 items-start">
        <form className="panel p-5 sm:p-6 space-y-5" onSubmit={e => { e.preventDefault(); create.mutate() }} noValidate>
          <Field label="Affected service" error={fields.serviceId}>
            <select className="input" value={f.serviceId} onChange={e => set('serviceId', e.target.value)} aria-invalid={!!fields.serviceId}>
              <option value="">Choose the service that's affected…</option>
              {catalog.data?.services.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
          <Field label="Short summary" error={fields.title} hint="e.g. “Payment page shows an error at checkout”">
            <input className="input" value={f.title} maxLength={200} onChange={e => set('title', e.target.value)} aria-invalid={!!fields.title} />
          </Field>
          <Field label="What's happening?" error={fields.description} hint="What you were doing, what you expected, what you saw, and since when.">
            <textarea className="input" rows={5} value={f.description} maxLength={4000} onChange={e => set('description', e.target.value)} aria-invalid={!!fields.description} />
          </Field>

          <Segmented legend="Who is affected?" options={IMPACT} value={f.impact} onChange={v => set('impact', v)} />
          <Segmented legend="How urgent is it?" options={URGENCY} value={f.urgency} onChange={v => set('urgency', v)} />

          <div className="grid sm:grid-cols-2 gap-4">
            <Field label="Roughly how many people?" error={fields.affectedUsers}>
              <input className="input mono" type="number" min={1} max={1000000} value={f.affectedUsers} onChange={e => set('affectedUsers', Number(e.target.value))} />
            </Field>
            <Field label="Category" hint={topCat && !f.categoryId ? `Auto-detect will choose ${topCat.name}` : 'Leave on auto-detect unless you are sure'}>
              <select className="input" value={f.categoryId} onChange={e => set('categoryId', e.target.value)}>
                <option value="">Auto-detect (recommended)</option>
                {catalog.data?.categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </Field>
          </div>

          {create.error && !Object.keys(fields).length ? <ErrorNote error={create.error} /> : null}
          {Object.keys(fields).length > 0 && <ErrorNote error={new Error('Some details need attention. Check the highlighted fields.')} />}
          <div className="flex items-center justify-end gap-2 pt-1">
            <Link to="/" className="btn-ghost">Cancel</Link>
            <button className="btn-primary" disabled={create.isPending}>{create.isPending ? <><Spinner />Triaging…</> : 'Submit incident'}</button>
          </div>
        </form>

        <aside className="space-y-4 xl:sticky xl:top-[88px]" aria-live="polite">
          <div className="panel">
            <div className="panel-head">
              <span className="panel-title flex items-center gap-2"><Sparkles className="size-4 text-action" />Live triage</span>
              {analysis.isFetching && <Spinner className="size-3.5 text-ink-3" />}
            </div>
            <div className="p-4 space-y-5">
              <div>
                <div className="text-[12px] font-semibold text-ink-3 mb-2">Priority</div>
                {a?.priority ? (
                  <div className="flex items-start gap-3">
                    <TriageTag priority={a.priority.priority} size="lg" />
                    <div className="min-w-0">
                      <div className="font-bold">{PRIORITY_LABEL[a.priority.priority]} <span className="mono text-ink-3 font-normal text-[12px]">score {a.priority.score}</span></div>
                      <p className="text-[12px] text-ink-3 leading-snug mt-0.5">{a.priority.explanation.split(' -> ')[0]}</p>
                    </div>
                  </div>
                ) : <p className="text-[13px] text-ink-3">Pick a service and impact to see the priority.</p>}
              </div>
              <div>
                <div className="text-[12px] font-semibold text-ink-3 mb-2">Service level</div>
                {a?.sla ? (
                  <dl className="grid grid-cols-2 gap-2">
                    <div className="rounded-md bg-sunken px-3 py-2"><dt className="text-[11.5px] text-ink-3">First response</dt><dd className="mono font-semibold">{minutes(a.sla.responseMinutes)}</dd></div>
                    <div className="rounded-md bg-sunken px-3 py-2"><dt className="text-[11.5px] text-ink-3">Resolution</dt><dd className="mono font-semibold">{minutes(a.sla.resolutionMinutes)}</dd></div>
                    <div className="col-span-2 text-[11.5px] text-ink-3">{a.sla.policy} SLA policy</div>
                  </dl>
                ) : <p className="text-[13px] text-ink-3">Calculated from the service's SLA policy.</p>}
              </div>
              <div>
                <div className="text-[12px] font-semibold text-ink-3 mb-2">Detected category</div>
                {analysis.isLoading ? <Skeleton className="h-8" /> : a?.categories.length ? (
                  <div className="flex flex-wrap gap-1.5">
                    {a.categories.map((c, i) => (
                      <button key={c.categoryId} type="button" onClick={() => set('categoryId', String(c.categoryId))}
                              className={cx('chip h-7 px-2.5 transition-colors', String(c.categoryId) === f.categoryId || (!f.categoryId && i === 0) ? 'bg-action-soft text-action border-action/30' : 'bg-surface text-ink-2 border-line hover:border-ink-3')}
                              title={`Matched: ${c.matchedKeywords.join(', ') || 'service default'}`}>
                        {c.name}<span className="mono font-normal opacity-75">{Math.round(c.confidence * 100)}%</span>
                      </button>
                    ))}
                  </div>
                ) : <p className="text-[13px] text-ink-3">Start describing the problem.</p>}
              </div>
            </div>
          </div>

          {a && a.similar.length > 0 && (
            <div className="panel border-p2/40">
              <div className="panel-head"><span className="panel-title flex items-center gap-2"><TriangleAlert className="size-4 text-p2" />Already being worked on?</span></div>
              <ul className="divide-y divide-line">
                {a.similar.map(s => (
                  <li key={s.id} className="px-4 py-3">
                    <div className="flex items-center gap-2"><span className="mono text-[12px] text-ink-3">{s.number}</span><StatusBadge status={s.status} /><span className="ml-auto mono text-[11.5px] text-ink-3">{Math.round(s.score * 100)}% match</span></div>
                    <div className="font-semibold text-[13px] mt-1">{s.title}</div>
                    <div className="text-[12px] text-ink-3">{s.service}{s.assignee ? ` · ${s.assignee} is on it` : ''}</div>
                  </li>
                ))}
              </ul>
              <p className="px-4 pb-3 text-[12px] text-ink-3">You can still submit; it will be linked to the existing incident and routed to the same technician.</p>
            </div>
          )}

          {a && a.knownFixes.length > 0 && (
            <div className="panel">
              <div className="panel-head"><span className="panel-title flex items-center gap-2"><Lightbulb className="size-4 text-p3" />Fixed before</span></div>
              <ul className="divide-y divide-line">
                {a.knownFixes.slice(0, 2).map(s => (
                  <li key={s.id} className="px-4 py-3">
                    <div className="font-semibold text-[13px]">{s.title}</div>
                    <p className="text-[12.5px] text-ink-2 mt-0.5">{s.resolution}</p>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </aside>
      </div>
    </>
  )
}

function Segmented<T extends string>({ legend, options, value, onChange }: { legend: string; options: { v: T; label: string; hint: string }[]; value: T; onChange: (v: T) => void }) {
  return (
    <fieldset>
      <legend className="field-label">{legend}</legend>
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
        {options.map(o => (
          <label key={o.v} className={cx('cursor-pointer rounded-md border px-3 py-2 transition-colors has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-action',
            value === o.v ? 'border-action bg-action-soft' : 'border-line-strong hover:bg-sunken')}>
            <input type="radio" className="sr-only" checked={value === o.v} onChange={() => onChange(o.v)} name={legend} />
            <span className={cx('block text-[13px] font-bold', value === o.v ? 'text-action' : 'text-ink')}>{o.label}</span>
            <span className="block text-[11.5px] text-ink-3 leading-snug">{o.hint}</span>
          </label>
        ))}
      </div>
    </fieldset>
  )
}
