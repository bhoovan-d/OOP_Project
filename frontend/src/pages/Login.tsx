import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRight } from 'lucide-react'
import { useAuth } from '../lib/auth'
import { ApiError, type Priority } from '../lib/api'
import { ErrorNote, Field, Spinner, PriorityTag } from '../components/ui'

const DEMO = [
  { role: 'Employee', email: 'rohan@itsm.local', password: 'User@123', note: 'Reports & tracks incidents' },
  { role: 'Technician', email: 'neha@itsm.local', password: 'Tech@123', note: 'Works the queue' },
  { role: 'Admin', email: 'admin@itsm.local', password: 'Admin@123', note: 'Operations console' },
]

const BOARD: { p: Priority; n: string; t: string; left: number; label: string }[] = [
  { p: 'P1', n: 'INC1105', t: 'Payment application unavailable', left: 0.18, label: '5:12' },
  { p: 'P2', n: 'INC1098', t: 'VPN connection drops every few minutes', left: 0.46, label: '1h 49m' },
  { p: 'P3', n: 'INC1101', t: 'Shared drive running out of space', left: 0.71, label: '5h 40m' },
  { p: 'P4', n: 'INC1093', t: 'External monitor flickering', left: 0.92, label: '2d 3h' },
]

export default function Login({ mode }: { mode: 'login' | 'register' }) {
  const { login, register } = useAuth()
  const nav = useNavigate()
  const [form, setForm] = useState({ email: '', password: '', fullName: '', department: '', phone: '' })
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm(f => ({ ...f, [k]: e.target.value }))
  const fields = error instanceof ApiError ? error.fields ?? {} : {}

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setBusy(true); setError(null)
    try {
      if (mode === 'login') await login(form.email, form.password)
      else await register({ fullName: form.fullName, email: form.email, password: form.password, department: form.department || undefined, phone: form.phone || undefined })
      nav('/', { replace: true })
    } catch (err) { setError(err) } finally { setBusy(false) }
  }

  return (
    <div className="min-h-dvh grid lg:grid-cols-[1.05fr_1fr]">
      <section className="hidden lg:flex flex-col bg-board text-chalk px-14 py-12">
        <span className="plate self-start inline-flex items-end h-11 px-4 pb-1 pt-4 font-display font-extrabold text-[24px] leading-none">I-ITSM</span>
        <div className="my-auto max-w-[560px]">
          <h1 className="font-display text-white text-[48px] leading-[1.02] font-bold">Every incident triaged the moment it's reported.</h1>
          <p className="mt-4 text-[16px] leading-relaxed text-chalk max-w-[46ch]">
            Classified, prioritised, put on an SLA clock and routed to the right technician automatically, with every step on the record.
          </p>
          <div className="mt-10 grid grid-cols-4 gap-3" aria-label="Example board">
            {[['14', 'Open'], ['3', 'At risk'], ['2', 'Breached'], ['93', 'SLA met %']].map(([v, l]) => (
              <div key={l}>
                <span className="plate flex items-end justify-center pt-4 pb-1 font-display font-extrabold text-[40px] leading-[0.9] tabular-nums">{v}</span>
                <span className="block text-center text-[11px] font-semibold uppercase tracking-[0.12em] mt-2">{l}</span>
              </div>
            ))}
          </div>
          <ul className="mt-6 divide-y divide-board-line border-y border-board-line" aria-label="Example queue">
            {BOARD.map(b => (
              <li key={b.n} className="flex items-center gap-3 py-2.5">
                <PriorityTag priority={b.p} />
                <span className="font-display font-bold text-[17px] text-white tabular-nums w-[74px]">{b.n}</span>
                <span className="min-w-0 flex-1 text-white text-[14px] truncate">{b.t}</span>
                <span className="flex items-center gap-[3px]" aria-hidden>
                  {Array.from({ length: 12 }).map((_, i) => (
                    <span key={i} className={`size-[7px] rounded-full border-[1.5px] ${i < Math.ceil((1 - b.left) * 12) ? (b.left < 0.25 ? 'bg-p1 border-p1' : 'bg-plate border-plate') : 'border-chalk/50'}`} />
                  ))}
                </span>
                <span className="font-display font-bold text-[16px] text-white w-14 text-right tabular-nums">{b.label}</span>
              </li>
            ))}
          </ul>
          <p className="mt-3 text-[12px] text-chalk/80">Illustrative board. Each dot is a twelfth of the SLA window; filled dots are time used.</p>
        </div>
        <p className="text-[12.5px] text-chalk/80">CS F213 OOP · Intelligent IT Service Management & Incident Resolution</p>
      </section>

      <section className="flex items-center justify-center px-5 py-12 bg-surface">
        <div className="w-full max-w-[400px]">
          <h2 className="font-display text-[34px] leading-tight font-bold">{mode === 'login' ? 'Sign in' : 'Create your account'}</h2>
          <p className="text-ink-3 mt-1">
            {mode === 'login'
              ? <>New here? <Link to="/register" className="text-action font-semibold hover:underline">Create an employee account</Link></>
              : <>Already registered? <Link to="/login" className="text-action font-semibold hover:underline">Sign in</Link></>}
          </p>

          <form onSubmit={submit} className="mt-7 space-y-4" noValidate>
            {mode === 'register' && (
              <Field label="Full name" error={fields.fullName}>
                <input className="input" autoComplete="name" value={form.fullName} onChange={set('fullName')} aria-invalid={!!fields.fullName} required />
              </Field>
            )}
            <Field label="Work email" error={fields.email}>
              <input className="input" type="email" autoComplete="email" value={form.email} onChange={set('email')} aria-invalid={!!fields.email} required />
            </Field>
            <Field label="Password" error={fields.password} hint={mode === 'register' ? 'At least 8 characters with a letter and a digit.' : undefined}>
              <input className="input" type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} value={form.password} onChange={set('password')} aria-invalid={!!fields.password} required />
            </Field>
            {mode === 'register' && (
              <div className="grid grid-cols-2 gap-3">
                <Field label="Department" error={fields.department}><input className="input" value={form.department} onChange={set('department')} /></Field>
                <Field label="Mobile (for SMS)" error={fields.phone}><input className="input" type="tel" value={form.phone} onChange={set('phone')} aria-invalid={!!fields.phone} /></Field>
              </div>
            )}
            {error && !Object.keys(fields).length ? <ErrorNote error={error} /> : null}
            <button className="btn-primary w-full h-10" disabled={busy}>
              {busy ? <Spinner /> : <>{mode === 'login' ? 'Sign in' : 'Create account'}<ArrowRight className="size-4" /></>}
            </button>
          </form>

          {mode === 'login' && (
            <div className="mt-9">
              <div className="text-[11.5px] font-semibold uppercase tracking-[0.12em] text-ink-3 mb-2">Demo accounts · seeded data</div>
              <div className="grid grid-cols-3 gap-2">
                {DEMO.map(d => (
                  <button key={d.role} type="button" className="text-left rounded-[4px] border border-line-strong hover:border-action hover:bg-action-soft px-3 py-2.5"
                          onClick={() => setForm(f => ({ ...f, email: d.email, password: d.password }))}>
                    <span className="block font-display text-[17px] font-bold">{d.role}</span>
                    <span className="block text-[12.5px] text-ink-3 leading-snug">{d.note}</span>
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
      </section>
    </div>
  )
}
