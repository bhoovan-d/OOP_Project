import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Mail, MessageSquareText, Pencil, Plus } from 'lucide-react'
import {
  ApiError, api, patch, post, put, qs, type AuditView, type CategoryView, type OutboxView, type Page, type Priority, type Role,
  type ServiceView, type SettingsView, type SlaView, type UserView,
} from '../lib/api'
import { fmtDateTime, minutes, titleCase } from '../lib/format'
import { CapacityBar, Dialog, Empty, ErrorNote, Field, PageHeader, Pager, SkeletonRows, Spinner, PriorityTag, cx } from '../components/ui'

export default function Admin() {
  const { section } = useParams()
  switch (section) {
    case 'users': return <Users />
    case 'services': return <Services />
    case 'sla': return <SlaPolicies />
    case 'categories': return <Categories />
    case 'settings': return <Automation />
    case 'audit': return <Audit />
    case 'outbox': return <Outbox />
    default: return <Empty title="Unknown section" />
  }
}

/* shared save helper: one mutation per form, invalidates its list, closes the dialog */
function useSave<T>(key: string, close: () => void) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (fn: () => Promise<T>) => fn(),
    onSuccess: () => { qc.invalidateQueries({ queryKey: [key] }); toast.success('Saved'); close() },
  })
}
const fieldErr = (e: unknown, k: string) => (e instanceof ApiError ? e.fields?.[k] : undefined)
const formErr = (e: unknown) => (e && !(e instanceof ApiError && e.fields) ? <ErrorNote error={e} /> : null)
const Footer = ({ busy, onCancel, label = 'Save' }: { busy: boolean; onCancel: () => void; label?: string }) => (
  <div className="flex justify-end gap-2 pt-2"><button type="button" className="btn-ghost" onClick={onCancel}>Cancel</button><button className="btn-primary" disabled={busy}>{busy ? <Spinner /> : label}</button></div>
)
const Check = ({ label, checked, onChange }: { label: string; checked: boolean; onChange: (v: boolean) => void }) => (
  <label className="inline-flex items-center gap-2 text-[13.5px] font-semibold text-ink-2 cursor-pointer"><input type="checkbox" className="size-4 accent-[var(--color-action)]" checked={checked} onChange={e => onChange(e.target.checked)} />{label}</label>
)

/* ===================================== People ===================================== */

function Users() {
  const [role, setRole] = useState<Role | ''>('')
  const [editing, setEditing] = useState<UserView | 'new' | null>(null)
  const users = useQuery({ queryKey: ['users'], queryFn: () => api<UserView[]>('/admin/users') })
  const cats = useQuery({ queryKey: ['categories'], queryFn: () => api<CategoryView[]>('/admin/categories') })
  const rows = (users.data ?? []).filter(u => !role || u.role === role)
  return (
    <>
      <PageHeader title="People" sub="Employees, technicians and administrators. Technician skills and limits drive automatic assignment."
                  actions={<button className="btn-primary" onClick={() => setEditing('new')}><Plus className="size-4" />Add person</button>} />
      <div className="panel">
        <div className="flex gap-1 px-2 border-b border-line" role="tablist">
          {(['', 'EMPLOYEE', 'TECHNICIAN', 'ADMIN'] as const).map(r => (
            <button key={r} role="tab" aria-selected={role === r} onClick={() => setRole(r)} className={cx('relative h-11 px-3 text-[13.5px] font-semibold', role === r ? 'text-ink' : 'text-ink-3 hover:text-ink')}>
              {r ? titleCase(r) + 's' : 'Everyone'}<span className="mono text-[15px] font-semibold text-ink-3 ml-1.5">{(users.data ?? []).filter(u => !r || u.role === r).length}</span>
              {role === r && <span className="absolute inset-x-2 -bottom-px h-[3px] bg-action" />}
            </button>
          ))}
        </div>
        {users.isLoading ? <SkeletonRows /> : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px]">
              <thead><tr><th className="th">Name</th><th className="th">Role</th><th className="th">Contact</th><th className="th">Skills / notes</th><th className="th w-[180px]">Workload</th><th className="th w-[70px]" /></tr></thead>
              <tbody>
                {rows.map(u => (
                  <tr key={u.id} className={cx('hover:bg-sunken/60', !u.active && 'opacity-55')}>
                    <td className="td"><div className="font-semibold text-[13.5px]">{u.fullName}</div><div className="mono text-[15px] font-semibold text-ink-3">{u.code}</div></td>
                    <td className="td text-[13.5px]">{titleCase(u.role)}{u.itManager && <span className="block text-[12px] text-ink-3">IT manager</span>}{u.teamLead && <span className="block text-[12px] text-ink-3">Team lead</span>}</td>
                    <td className="td text-[13px] text-ink-2">{u.email}<div className="text-ink-3">{u.phone ?? 'No phone (no SMS)'}</div></td>
                    <td className="td text-[13px] text-ink-2">{u.role === 'TECHNICIAN' ? <>{u.skills?.map(s => s.name).join(', ')}<div className="text-ink-3">{u.experienceYears} yrs{u.available ? '' : ' · unavailable'}</div></> : u.department ?? '—'}{!u.active && <div className="text-bad font-semibold">Deactivated</div>}</td>
                    <td className="td">{u.role === 'TECHNICIAN' ? <CapacityBar value={u.activeLoad ?? 0} max={u.maxWorkload ?? 1} /> : <span className="text-ink-3">—</span>}</td>
                    <td className="td"><button className="btn-ghost btn-sm" onClick={() => setEditing(u)} aria-label={`Edit ${u.fullName}`}><Pencil className="size-4" /></button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      {editing && <UserForm key={editing === 'new' ? 'new' : editing.id} user={editing === 'new' ? null : editing} categories={cats.data ?? []} close={() => setEditing(null)} />}
    </>
  )
}

function UserForm({ user, categories, close }: { user: UserView | null; categories: CategoryView[]; close: () => void }) {
  const [f, setF] = useState({
    role: (user?.role ?? 'EMPLOYEE') as Role, fullName: user?.fullName ?? '', email: user?.email ?? '', password: '', department: user?.department ?? '',
    phone: user?.phone ?? '', active: user?.active ?? true, skillIds: user?.skills?.map(s => s.id) ?? [] as number[], maxWorkload: user?.maxWorkload ?? 5,
    experienceYears: user?.experienceYears ?? 0, available: user?.available ?? true, teamLead: user?.teamLead ?? false, itManager: user?.itManager ?? false,
  })
  const [newPw, setNewPw] = useState('')
  const set = <K extends keyof typeof f>(k: K, v: (typeof f)[K]) => setF(s => ({ ...s, [k]: v }))
  const save = useSave('users', close)
  const pw = useMutation({ mutationFn: () => post(`/admin/users/${user!.id}/password`, { password: newPw }), onSuccess: () => { toast.success('Password reset'); setNewPw('') } })
  const tech = f.role === 'TECHNICIAN'
  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    const techFields = tech ? { skillIds: f.skillIds, maxWorkload: f.maxWorkload, experienceYears: f.experienceYears, teamLead: f.teamLead } : {}
    save.mutate(() => user
      ? patch(`/admin/users/${user.id}`, { fullName: f.fullName, department: f.department, phone: f.phone, active: f.active, ...techFields, available: tech ? f.available : undefined, itManager: f.role === 'ADMIN' ? f.itManager : undefined })
      : post('/admin/users', { role: f.role, fullName: f.fullName, email: f.email, password: f.password, department: f.department, phone: f.phone, ...techFields, itManager: f.itManager }))
  }
  return (
    <Dialog open onClose={close} title={user ? `Edit ${user.fullName}` : 'Add a person'} wide>
      <form className="space-y-4" onSubmit={submit} noValidate>
        <div className="grid sm:grid-cols-2 gap-3">
          {!user && <Field label="Role"><select className="input" value={f.role} onChange={e => set('role', e.target.value as Role)}>{['EMPLOYEE', 'TECHNICIAN', 'ADMIN'].map(r => <option key={r} value={r}>{titleCase(r)}</option>)}</select></Field>}
          <Field label="Full name" error={fieldErr(save.error, 'fullName')}><input className="input" value={f.fullName} onChange={e => set('fullName', e.target.value)} /></Field>
          {!user && <Field label="Email" error={fieldErr(save.error, 'email')}><input className="input" type="email" value={f.email} onChange={e => set('email', e.target.value)} /></Field>}
          {!user && <Field label="Initial password" error={fieldErr(save.error, 'password')} hint="8+ characters, a letter and a digit"><input className="input" type="password" autoComplete="new-password" value={f.password} onChange={e => set('password', e.target.value)} /></Field>}
          <Field label="Department"><input className="input" value={f.department} onChange={e => set('department', e.target.value)} /></Field>
          <Field label="Mobile (SMS alerts)" error={fieldErr(save.error, 'phone')}><input className="input" value={f.phone} onChange={e => set('phone', e.target.value)} /></Field>
        </div>
        {tech && (
          <div className="rounded-[6px] bg-sunken/70 p-4 space-y-3">
            <div>
              <span className="field-label">Skills</span>
              <div className="flex flex-wrap gap-1.5">
                {categories.filter(c => c.active).map(c => {
                  const on = f.skillIds.includes(c.id)
                  return <button type="button" key={c.id} aria-pressed={on} onClick={() => set('skillIds', on ? f.skillIds.filter(x => x !== c.id) : [...f.skillIds, c.id])}
                                 className={cx('chip h-7 px-2.5', on ? 'bg-action-soft text-action border-action/30' : 'bg-surface text-ink-2 border-line hover:border-ink-3')}>{c.name}</button>
                })}
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Max concurrent incidents" error={fieldErr(save.error, 'maxWorkload')}><input className="input mono" type="number" min={1} max={50} value={f.maxWorkload} onChange={e => set('maxWorkload', Number(e.target.value))} /></Field>
              <Field label="Years of experience" error={fieldErr(save.error, 'experienceYears')}><input className="input mono" type="number" min={0} max={60} value={f.experienceYears} onChange={e => set('experienceYears', Number(e.target.value))} /></Field>
            </div>
            <div className="flex flex-wrap gap-5">
              <Check label="Team lead (escalation level 1)" checked={f.teamLead} onChange={v => set('teamLead', v)} />
              {user && <Check label="Available for new work" checked={f.available} onChange={v => set('available', v)} />}
            </div>
          </div>
        )}
        {f.role === 'ADMIN' && <Check label="IT manager (top of the escalation chain)" checked={f.itManager} onChange={v => set('itManager', v)} />}
        {user && <Check label="Account active" checked={f.active} onChange={v => set('active', v)} />}
        {formErr(save.error)}
        <Footer busy={save.isPending} onCancel={close} label={user ? 'Save changes' : 'Create account'} />
      </form>
      {user && (
        <form className="mt-5 pt-4 border-t border-line flex items-end gap-2" onSubmit={e => { e.preventDefault(); pw.mutate() }}>
          <Field label="Reset password" error={fieldErr(pw.error, 'password')}><input className="input" type="password" autoComplete="new-password" value={newPw} onChange={e => setNewPw(e.target.value)} /></Field>
          <button className="btn-secondary mb-[1px]" disabled={!newPw || pw.isPending}>Reset</button>
        </form>
      )}
    </Dialog>
  )
}

/* ===================================== Services ===================================== */

function Services() {
  const [editing, setEditing] = useState<ServiceView | 'new' | null>(null)
  const services = useQuery({ queryKey: ['services'], queryFn: () => api<ServiceView[]>('/admin/services') })
  return (
    <>
      <PageHeader title="IT services" sub="Criticality feeds the priority score; each service can carry its own SLA policy."
                  actions={<button className="btn-primary" onClick={() => setEditing('new')}><Plus className="size-4" />Add service</button>} />
      <div className="panel overflow-x-auto">
        {services.isLoading ? <SkeletonRows /> : (
          <table className="w-full min-w-[760px]">
            <thead><tr><th className="th">Service</th><th className="th">Criticality</th><th className="th">SLA policy</th><th className="th">Default category</th><th className="th text-right">Users</th><th className="th w-[70px]" /></tr></thead>
            <tbody>
              {services.data!.map(s => (
                <tr key={s.id} className={cx('hover:bg-sunken/60', !s.active && 'opacity-55')}>
                  <td className="td"><div className="font-semibold text-[13.5px]">{s.name}</div><div className="text-[12.5px] text-ink-3">{s.description}</div></td>
                  <td className="td text-[13.5px]">{titleCase(s.criticality)}</td>
                  <td className="td text-[13.5px]">{s.slaPolicy?.name ?? <span className="text-ink-3">Default</span>}</td>
                  <td className="td text-[13.5px]">{s.defaultCategory?.name ?? '—'}</td>
                  <td className="td text-right mono text-[16px] font-semibold">{s.userBase.toLocaleString()}</td>
                  <td className="td"><button className="btn-ghost btn-sm" onClick={() => setEditing(s)} aria-label={`Edit ${s.name}`}><Pencil className="size-4" /></button></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {editing && <ServiceForm svc={editing === 'new' ? null : editing} close={() => setEditing(null)} />}
    </>
  )
}

function ServiceForm({ svc, close }: { svc: ServiceView | null; close: () => void }) {
  const policies = useQuery({ queryKey: ['sla'], queryFn: () => api<SlaView[]>('/admin/sla-policies') })
  const cats = useQuery({ queryKey: ['categories'], queryFn: () => api<CategoryView[]>('/admin/categories') })
  const [f, setF] = useState({ name: svc?.name ?? '', description: svc?.description ?? '', criticality: svc?.criticality ?? 'MODERATE', slaPolicyId: svc?.slaPolicy?.id ?? '', defaultCategoryId: svc?.defaultCategory?.id ?? '', userBase: svc?.userBase ?? 0, active: svc?.active ?? true })
  const set = <K extends keyof typeof f>(k: K, v: (typeof f)[K]) => setF(s => ({ ...s, [k]: v }))
  const save = useSave('services', close)
  const body = () => ({ ...f, slaPolicyId: f.slaPolicyId || null, defaultCategoryId: f.defaultCategoryId || null })
  return (
    <Dialog open onClose={close} title={svc ? `Edit ${svc.name}` : 'Add an IT service'}>
      <form className="space-y-4" onSubmit={e => { e.preventDefault(); save.mutate(() => svc ? put(`/admin/services/${svc.id}`, body()) : post('/admin/services', body())) }}>
        <Field label="Name" error={fieldErr(save.error, 'name')}><input className="input" value={f.name} onChange={e => set('name', e.target.value)} /></Field>
        <Field label="Description"><input className="input" value={f.description} onChange={e => set('description', e.target.value)} /></Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Business criticality"><select className="input" value={f.criticality} onChange={e => set('criticality', e.target.value)}>{['LOW', 'MODERATE', 'HIGH', 'CRITICAL', 'MISSION_CRITICAL'].map(c => <option key={c} value={c}>{titleCase(c)}</option>)}</select></Field>
          <Field label="Users served"><input className="input mono" type="number" min={0} value={f.userBase} onChange={e => set('userBase', Number(e.target.value))} /></Field>
          <Field label="SLA policy"><select className="input" value={f.slaPolicyId} onChange={e => set('slaPolicyId', e.target.value ? Number(e.target.value) : '')}><option value="">Use default policy</option>{policies.data?.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select></Field>
          <Field label="Default category"><select className="input" value={f.defaultCategoryId} onChange={e => set('defaultCategoryId', e.target.value ? Number(e.target.value) : '')}><option value="">None</option>{cats.data?.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></Field>
        </div>
        <Check label="Active (offered when reporting)" checked={f.active} onChange={v => set('active', v)} />
        {formErr(save.error)}
        <Footer busy={save.isPending} onCancel={close} />
      </form>
    </Dialog>
  )
}

/* ===================================== SLA ===================================== */

const P: Priority[] = ['P1', 'P2', 'P3', 'P4']

function SlaPolicies() {
  const [editing, setEditing] = useState<SlaView | 'new' | null>(null)
  const q = useQuery({ queryKey: ['sla'], queryFn: () => api<SlaView[]>('/admin/sla-policies') })
  return (
    <>
      <PageHeader title="SLA policies" sub="Response and resolution targets per priority. Services without a policy use the default."
                  actions={<button className="btn-primary" onClick={() => setEditing('new')}><Plus className="size-4" />New policy</button>} />
      <div className="grid lg:grid-cols-3 gap-5">
        {q.data?.map(p => (
          <section key={p.id} className="panel">
            <div className="panel-head">
              <span className="panel-title">{p.name}{p.defaultPolicy && <span className="chip h-5 ml-2 bg-action-soft text-action border-action/25">Default</span>}</span>
              <button className="btn-ghost btn-sm" onClick={() => setEditing(p)} aria-label={`Edit ${p.name}`}><Pencil className="size-4" /></button>
            </div>
            <p className="px-4 pt-3 text-[13px] text-ink-3">{p.description}</p>
            <table className="w-full mt-2">
              <thead><tr><th className="th">Priority</th><th className="th text-right">Respond</th><th className="th text-right">Resolve</th></tr></thead>
              <tbody>{P.map(pr => (
                <tr key={pr}><td className="td"><PriorityTag priority={pr} size="sm" /></td><td className="td text-right mono text-[16px] font-semibold">{minutes(p.targets[pr].responseMinutes)}</td><td className="td text-right mono text-[16px] font-semibold">{minutes(p.targets[pr].resolutionMinutes)}</td></tr>
              ))}</tbody>
            </table>
          </section>
        ))}
      </div>
      {editing && <SlaForm policy={editing === 'new' ? null : editing} close={() => setEditing(null)} />}
    </>
  )
}

function SlaForm({ policy, close }: { policy: SlaView | null; close: () => void }) {
  const [f, setF] = useState({ name: policy?.name ?? '', description: policy?.description ?? '', defaultPolicy: policy?.defaultPolicy ?? false,
    targets: policy?.targets ?? { P1: { responseMinutes: 15, resolutionMinutes: 60 }, P2: { responseMinutes: 30, resolutionMinutes: 240 }, P3: { responseMinutes: 120, resolutionMinutes: 960 }, P4: { responseMinutes: 480, resolutionMinutes: 2880 } } })
  const save = useSave('sla', close)
  const setT = (p: Priority, k: 'responseMinutes' | 'resolutionMinutes', v: number) => setF(s => ({ ...s, targets: { ...s.targets, [p]: { ...s.targets[p], [k]: v } } }))
  return (
    <Dialog open onClose={close} title={policy ? `Edit ${policy.name}` : 'New SLA policy'}>
      <form className="space-y-4" onSubmit={e => { e.preventDefault(); save.mutate(() => policy ? put(`/admin/sla-policies/${policy.id}`, f) : post('/admin/sla-policies', f)) }}>
        <Field label="Name" error={fieldErr(save.error, 'name')}><input className="input" value={f.name} onChange={e => setF(s => ({ ...s, name: e.target.value }))} /></Field>
        <Field label="Description"><input className="input" value={f.description} onChange={e => setF(s => ({ ...s, description: e.target.value }))} /></Field>
        <table className="w-full">
          <thead><tr><th className="th">Priority</th><th className="th">Respond (min)</th><th className="th">Resolve (min)</th></tr></thead>
          <tbody>{P.map(p => (
            <tr key={p}>
              <td className="td"><PriorityTag priority={p} size="sm" /></td>
              <td className="td"><input className="input mono h-8" type="number" min={1} value={f.targets[p].responseMinutes} onChange={e => setT(p, 'responseMinutes', Number(e.target.value))} aria-label={`${p} response minutes`} /></td>
              <td className="td"><input className="input mono h-8" type="number" min={1} value={f.targets[p].resolutionMinutes} onChange={e => setT(p, 'resolutionMinutes', Number(e.target.value))} aria-label={`${p} resolution minutes`} /></td>
            </tr>
          ))}</tbody>
        </table>
        <Check label="Default policy for services without one" checked={f.defaultPolicy} onChange={v => setF(s => ({ ...s, defaultPolicy: v }))} />
        {formErr(save.error)}
        <Footer busy={save.isPending} onCancel={close} />
      </form>
    </Dialog>
  )
}

/* ===================================== Categories ===================================== */

function Categories() {
  const [editing, setEditing] = useState<CategoryView | 'new' | null>(null)
  const q = useQuery({ queryKey: ['categories'], queryFn: () => api<CategoryView[]>('/admin/categories') })
  return (
    <>
      <PageHeader title="Incident categories" sub="Keywords teach the auto-classifier. Adding a category needs no code change."
                  actions={<button className="btn-primary" onClick={() => setEditing('new')}><Plus className="size-4" />New category</button>} />
      <div className="panel divide-y divide-line">
        {q.isLoading ? <SkeletonRows /> : q.data!.map(c => (
          <div key={c.id} className={cx('px-4 py-3.5 flex items-start gap-4', !c.active && 'opacity-55')}>
            <div className="w-[180px] shrink-0"><div className="font-bold text-[13.5px]">{c.name}</div><div className="text-[12.5px] text-ink-3">{c.description}</div></div>
            <div className="flex-1 flex flex-wrap gap-1.5">{c.keywords.map(k => <span key={k} className="chip h-6 bg-sunken text-ink-2 border-line font-mono font-normal">{k}</span>)}</div>
            <button className="btn-ghost btn-sm" onClick={() => setEditing(c)} aria-label={`Edit ${c.name}`}><Pencil className="size-4" /></button>
          </div>
        ))}
      </div>
      {editing && <CategoryForm cat={editing === 'new' ? null : editing} close={() => setEditing(null)} />}
    </>
  )
}

function CategoryForm({ cat, close }: { cat: CategoryView | null; close: () => void }) {
  const [f, setF] = useState({ name: cat?.name ?? '', description: cat?.description ?? '', keywords: cat?.keywords.join(', ') ?? '', active: cat?.active ?? true })
  const save = useSave('categories', close)
  const body = () => ({ ...f, keywords: f.keywords.split(',').map(s => s.trim()).filter(Boolean) })
  return (
    <Dialog open onClose={close} title={cat ? `Edit ${cat.name}` : 'New category'}>
      <form className="space-y-4" onSubmit={e => { e.preventDefault(); save.mutate(() => cat ? put(`/admin/categories/${cat.id}`, body()) : post('/admin/categories', body())) }}>
        <Field label="Name" error={fieldErr(save.error, 'name')}><input className="input" value={f.name} onChange={e => setF(s => ({ ...s, name: e.target.value }))} /></Field>
        <Field label="Description"><input className="input" value={f.description} onChange={e => setF(s => ({ ...s, description: e.target.value }))} /></Field>
        <Field label="Classifier keywords" hint="Comma separated. Words in an incident's title count double."><textarea className="input font-mono text-[16px] font-semibold" rows={3} value={f.keywords} onChange={e => setF(s => ({ ...s, keywords: e.target.value }))} /></Field>
        <Check label="Active" checked={f.active} onChange={v => setF(s => ({ ...s, active: v }))} />
        {formErr(save.error)}
        <Footer busy={save.isPending} onCancel={close} />
      </form>
    </Dialog>
  )
}

/* ===================================== Automation ===================================== */

function Automation() {
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['settings'], queryFn: () => api<SettingsView>('/admin/settings') })
  const save = useMutation({
    mutationFn: (body: Partial<SettingsView>) => put<SettingsView>('/admin/settings', body),
    onSuccess: d => { qc.setQueryData(['settings'], d); toast.success('Automation updated') },
    onError: e => toast.error((e as Error).message),
  })
  const s = q.data
  if (!s) return <SkeletonRows />
  return (
    <>
      <PageHeader title="Automation" sub="How new incidents are routed and how the SLA watchdog behaves. Changes apply immediately." />
      <section className="panel mb-5">
        <div className="panel-head"><span className="panel-title">Assignment strategy</span><span className="text-[12.5px] text-ink-3">used for every automatic assignment</span></div>
        <div className="grid md:grid-cols-2 gap-3 p-4" role="radiogroup" aria-label="Assignment strategy">
          {s.strategies.map(st => (
            <button key={st.key} role="radio" aria-checked={st.active} onClick={() => save.mutate({ assignmentStrategy: st.key })}
                    className={cx('text-left rounded-[6px] border p-4 transition-colors', st.active ? 'border-action bg-action-soft/60' : 'border-line hover:border-ink-3')}>
              <div className="flex items-center gap-2"><span className={cx('size-4 rounded-full border-2 grid place-items-center', st.active ? 'border-action' : 'border-line-strong')}>{st.active && <span className="size-2 rounded-full bg-action" />}</span><span className="font-bold">{st.name}</span></div>
              <p className="text-[13px] text-ink-3 mt-1 pl-6">{st.description}</p>
            </button>
          ))}
        </div>
      </section>
      <section className="panel">
        <div className="panel-head"><span className="panel-title">SLA watchdog</span></div>
        <div className="p-4 space-y-5">
          <Setting title="Auto-assign new incidents" desc="When off, new incidents wait in the unassigned queue for a person to route them.">
            <Switch on={s.autoAssign} onChange={v => save.mutate({ autoAssign: v })} label="Auto-assign" />
          </Setting>
          <Setting title="Automatic escalation" desc="Unacknowledged P1/P2 incidents escalate Technician → Team lead → System admin → IT manager, one level per missed response window.">
            <Switch on={s.escalationEnabled} onChange={v => save.mutate({ escalationEnabled: v })} label="Escalation" />
          </Setting>
          <Setting title="Warn before deadline" desc={`An incident is flagged “at risk” once ${Math.round(s.slaWarningThreshold * 100)}% of its SLA window has passed.`}>
            <input type="range" min={0.5} max={0.95} step={0.05} defaultValue={s.slaWarningThreshold} className="w-40 accent-[var(--color-action)]"
                   onMouseUp={e => save.mutate({ slaWarningThreshold: Number((e.target as HTMLInputElement).value) })}
                   onKeyUp={e => save.mutate({ slaWarningThreshold: Number((e.target as HTMLInputElement).value) })} aria-label="Warning threshold" />
            <span className="mono text-[16px] font-semibold w-10 text-right">{Math.round(s.slaWarningThreshold * 100)}%</span>
          </Setting>
        </div>
      </section>
    </>
  )
}

const Setting = ({ title, desc, children }: { title: string; desc: string; children: ReactNode }) => (
  <div className="flex flex-wrap items-center gap-4 justify-between"><div className="max-w-[60ch]"><div className="font-bold text-[13.5px]">{title}</div><div className="text-[13px] text-ink-3">{desc}</div></div><div className="flex items-center gap-3">{children}</div></div>
)
const Switch = ({ on, onChange, label }: { on: boolean; onChange: (v: boolean) => void; label: string }) => (
  <button role="switch" aria-checked={on} aria-label={label} onClick={() => onChange(!on)} className={cx('relative w-11 h-6 rounded-full transition-colors', on ? 'bg-action' : 'bg-line-strong')}>
    <span className={cx('absolute top-0.5 size-5 rounded-full bg-white shadow transition-transform', on ? 'translate-x-[22px]' : 'translate-x-0.5')} />
  </button>
)

/* ===================================== Audit ===================================== */

const ACTIONS = ['CREATED', 'CLASSIFIED', 'PRIORITIZED', 'PRIORITY_CHANGED', 'ASSIGNED', 'ACKNOWLEDGED', 'WORK_STARTED', 'INFO_REQUESTED', 'INFO_PROVIDED', 'RESOLVED', 'VERIFIED', 'REOPENED', 'CLOSED', 'CANCELLED', 'SLA_WARNING', 'SLA_BREACHED', 'ESCALATED', 'DUPLICATE_DETECTED', 'MAJOR_INCIDENT', 'UPDATED']

function Audit() {
  const [f, setF] = useState({ incident: '', action: '', actor: '' })
  const [page, setPage] = useState(0)
  const params = { ...f, page, size: 40 }
  const q = useQuery({ queryKey: ['audit', params], queryFn: () => api<Page<AuditView>>(`/admin/audit${qs(params)}`), placeholderData: p => p })
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => { setF(s => ({ ...s, [k]: e.target.value })); setPage(0) }
  return (
    <>
      <PageHeader title="Audit log" sub="Every action on every incident, plus administrative changes. Records are append-only." />
      <div className="panel">
        <div className="p-4 border-b border-line grid sm:grid-cols-3 gap-3">
          <Field label="Incident"><input className="input mono" placeholder="INC1042" value={f.incident} onChange={set('incident')} /></Field>
          <Field label="Action"><select className="input" value={f.action} onChange={set('action')}><option value="">All actions</option>{ACTIONS.map(a => <option key={a} value={a}>{titleCase(a)}</option>)}</select></Field>
          <Field label="Actor"><input className="input" placeholder="Name or System" value={f.actor} onChange={set('actor')} /></Field>
        </div>
        {q.isLoading ? <SkeletonRows rows={10} /> : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[860px]">
              <thead><tr><th className="th w-[140px]">Time</th><th className="th w-[100px]">Incident</th><th className="th w-[150px]">Actor</th><th className="th w-[150px]">Action</th><th className="th">Description</th><th className="th w-[220px]">State change</th></tr></thead>
              <tbody>
                {q.data!.content.map(a => (
                  <tr key={a.id} className="hover:bg-sunken/60 align-top">
                    <td className="td mono text-[15px] font-semibold text-ink-2">{fmtDateTime(a.timestamp)}</td>
                    <td className="td">{a.incidentId ? <Link to={`/incidents/${a.incidentId}`} className="mono text-[15px] font-semibold text-action hover:underline">{a.incidentNumber}</Link> : <span className="text-ink-3 text-[12.5px]">Admin</span>}</td>
                    <td className="td text-[13.5px]">{a.actor}</td>
                    <td className={cx('td text-[13px] font-semibold', /BREACH|ESCALAT|MAJOR/.test(a.action) && 'text-bad')}>{titleCase(a.action)}</td>
                    <td className="td text-[13.5px] text-ink-2">{a.description}</td>
                    <td className="td mono text-[15px] font-semibold text-ink-3">{a.fromState && a.toState && a.fromState !== a.toState ? `${a.fromState} → ${a.toState}` : ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pager page={page} totalPages={q.data!.totalPages} total={q.data!.totalElements} onPage={setPage} />
          </div>
        )}
      </div>
    </>
  )
}

/* ===================================== Outbox ===================================== */

function Outbox() {
  const [page, setPage] = useState(0)
  const q = useQuery({ queryKey: ['outbox', page], queryFn: () => api<Page<OutboxView>>(`/admin/outbox${qs({ page, size: 30 })}`), refetchInterval: 15_000 })
  return (
    <>
      <PageHeader title="Email & SMS outbox" sub="Messages sent through the email channel and the SMS gateway adapter (simulated delivery)." />
      <div className="panel">
        {q.isLoading ? <SkeletonRows rows={8} /> : !q.data!.content.length ? <Empty title="Nothing sent yet">Messages appear here as incidents are assigned, escalated or resolved.</Empty> : (
          <>
            <ul className="divide-y divide-line">
              {q.data!.content.map(n => (
                <li key={n.id} className="px-4 py-3 grid grid-cols-[28px_1fr_auto] gap-3">
                  <span className={cx('size-7 rounded-[3px] grid place-items-center', n.channel === 'SMS' ? 'bg-[#efeaf7] text-[#553a8f]' : 'bg-action-soft text-action')}>
                    {n.channel === 'SMS' ? <MessageSquareText className="size-4" /> : <Mail className="size-4" />}
                  </span>
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 text-[13px]"><span className="font-bold">{n.title}</span><span className="text-ink-3 truncate">to {n.recipient}</span></div>
                    <p className="text-[13px] text-ink-2 whitespace-pre-line line-clamp-3 max-w-[90ch]">{n.message}</p>
                  </div>
                  <div className="text-right text-[12px] text-ink-3"><div className="mono">{fmtDateTime(n.createdAt)}</div><div className="mono">{n.externalId}</div></div>
                </li>
              ))}
            </ul>
            <Pager page={page} totalPages={q.data!.totalPages} total={q.data!.totalElements} onPage={setPage} />
          </>
        )}
      </div>
    </>
  )
}
