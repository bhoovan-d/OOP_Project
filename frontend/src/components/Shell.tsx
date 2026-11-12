import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Bell, BookUser, ClipboardList, FileClock, Gauge, Inbox, LayoutGrid, ListChecks, LogOut, Menu, Plus,
  Search, Send, Server, Settings2, ShieldCheck, Tags, Timer, X,
} from 'lucide-react'
import { api, post, type NotificationView, type Role } from '../lib/api'
import { useAuth, useUser } from '../lib/auth'
import { useLive, useNow } from '../lib/live'
import { ago } from '../lib/format'
import { cx } from './ui'

type NavItem = { to: string; label: string; icon: ReactNode }
const I = 'size-[17px]'

const NAV: Record<Role, { section?: string; items: NavItem[] }[]> = {
  EMPLOYEE: [{ items: [
    { to: '/my', label: 'My incidents', icon: <ClipboardList className={I} /> },
    { to: '/report', label: 'Report an incident', icon: <Plus className={I} /> },
  ] }],
  TECHNICIAN: [{ items: [
    { to: '/queue', label: 'My queue', icon: <Inbox className={I} /> },
    { to: '/incidents?unassigned=true', label: 'Unassigned', icon: <ListChecks className={I} /> },
    { to: '/incidents', label: 'All incidents', icon: <LayoutGrid className={I} /> },
    { to: '/report', label: 'Log an incident', icon: <Plus className={I} /> },
  ] }],
  ADMIN: [
    { items: [
      { to: '/dashboard', label: 'Operations', icon: <Gauge className={I} /> },
      { to: '/incidents', label: 'Incidents', icon: <LayoutGrid className={I} /> },
      { to: '/incidents?breached=true', label: 'SLA violations', icon: <Timer className={I} /> },
    ] },
    { section: 'Configure', items: [
      { to: '/admin/users', label: 'People', icon: <BookUser className={I} /> },
      { to: '/admin/services', label: 'IT services', icon: <Server className={I} /> },
      { to: '/admin/sla', label: 'SLA policies', icon: <ShieldCheck className={I} /> },
      { to: '/admin/categories', label: 'Categories', icon: <Tags className={I} /> },
      { to: '/admin/settings', label: 'Automation', icon: <Settings2 className={I} /> },
    ] },
    { section: 'Records', items: [
      { to: '/admin/audit', label: 'Audit log', icon: <FileClock className={I} /> },
      { to: '/admin/outbox', label: 'Email & SMS outbox', icon: <Send className={I} /> },
    ] },
  ],
}

const ROLE_NAME: Record<Role, string> = { EMPLOYEE: 'Employee portal', TECHNICIAN: 'Technician workspace', ADMIN: 'IT operations console' }

export function Shell({ children }: { children: ReactNode }) {
  const user = useUser()
  const [open, setOpen] = useState(false)
  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[248px_1fr]">
      <aside className={cx('fixed inset-y-0 left-0 z-40 w-[248px] bg-rail text-rail-ink flex flex-col transition-transform duration-200 lg:sticky lg:top-0 lg:h-dvh lg:translate-x-0',
        open ? 'translate-x-0' : '-translate-x-full')}>
        <div className="flex items-center gap-2.5 px-5 h-16 border-b border-white/8">
          <Logo />
          <div className="leading-tight">
            <div className="text-white font-extrabold tracking-tight text-[15px]">I-ITSM</div>
            <div className="text-[11.5px] text-rail-ink/80">{ROLE_NAME[user.role]}</div>
          </div>
          <button className="ml-auto lg:hidden text-rail-ink p-1" onClick={() => setOpen(false)} aria-label="Close menu"><X className="size-5" /></button>
        </div>
        <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-5" onClick={() => setOpen(false)}>
          {NAV[user.role].map((g, gi) => (
            <div key={gi}>
              {g.section && <div className="px-3 mb-1.5 text-[11px] font-semibold uppercase tracking-[0.08em] text-rail-ink/55">{g.section}</div>}
              <ul className="space-y-0.5">
                {g.items.map(it => <li key={it.to}><RailLink {...it} /></li>)}
              </ul>
            </div>
          ))}
        </nav>
        <UserCard />
      </aside>
      {open && <div className="fixed inset-0 z-30 bg-rail/40 lg:hidden" onClick={() => setOpen(false)} />}
      <div className="min-w-0 flex flex-col">
        <TopBar onMenu={() => setOpen(true)} />
        <main className="flex-1 px-4 sm:px-6 lg:px-8 py-6 max-w-[1480px] w-full mx-auto">{children}</main>
      </div>
    </div>
  )
}

function RailLink({ to, label, icon }: NavItem) {
  const loc = useLocation()
  const [path, query] = to.split('?')
  const active = loc.pathname === path
    ? (query ? loc.search.includes(query) : !/unassigned|breached/.test(loc.search))
    : !query && loc.pathname.startsWith(path + '/')
  return (
    <Link to={to} aria-current={active ? 'page' : undefined}
      className={cx('flex items-center gap-3 px-3 h-9 rounded-md text-[13.5px] font-semibold transition-colors',
        active ? 'bg-rail-2 text-white' : 'text-rail-ink hover:bg-white/6 hover:text-white')}>
      {icon}<span>{label}</span>
    </Link>
  )
}

function Logo() {
  return (
    <svg viewBox="0 0 32 32" className="size-8 shrink-0" aria-hidden>
      <rect width="32" height="32" rx="7" fill="#1a3358" />
      <path d="M9 8h10l4 4v12H9z" fill="#fff" />
      <rect x="9" y="8" width="14" height="5" fill="#d42a1e" />
      <circle cx="12" cy="10.5" r="1.1" fill="#fff" />
      <rect x="12" y="16" width="8" height="1.6" rx=".8" fill="#0f2341" />
      <rect x="12" y="19.5" width="5" height="1.6" rx=".8" fill="#0f2341" />
    </svg>
  )
}

function UserCard() {
  const user = useUser()
  const { logout } = useAuth()
  const initials = user.fullName.split(' ').map(s => s[0]).slice(0, 2).join('')
  return (
    <div className="border-t border-white/8 p-3 flex items-center gap-3">
      <span className="size-9 rounded-full bg-rail-2 text-white grid place-items-center text-[12.5px] font-bold">{initials}</span>
      <div className="min-w-0 flex-1 leading-tight">
        <div className="text-white text-[13px] font-semibold truncate">{user.fullName}</div>
        <div className="text-[11.5px] text-rail-ink/75 mono">{user.code}{user.itManager ? ' · IT manager' : ''}</div>
      </div>
      <button className="p-2 rounded-md text-rail-ink hover:text-white hover:bg-white/6" onClick={logout} aria-label="Sign out" title="Sign out">
        <LogOut className="size-4" />
      </button>
    </div>
  )
}

function TopBar({ onMenu }: { onMenu: () => void }) {
  const user = useUser()
  const nav = useNavigate()
  const { connected } = useLive()
  const [q, setQ] = useState('')
  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    const t = q.trim()
    if (!t) return
    const num = t.match(/^(?:inc)?-?\s*(\d+)$/i)
    if (num) { const n = Number(num[1]); nav(`/incidents/${n >= 1000 ? n - 1000 : n}`) }
    else nav(`${user.role === 'EMPLOYEE' ? '/my' : '/incidents'}?q=${encodeURIComponent(t)}`)
    setQ('')
  }
  return (
    <header className="sticky top-0 z-20 h-16 bg-surface/95 backdrop-blur-[2px] border-b border-line flex items-center gap-3 px-4 sm:px-6 lg:px-8">
      <button className="lg:hidden btn-ghost btn-sm" onClick={onMenu} aria-label="Open menu"><Menu className="size-5" /></button>
      <form onSubmit={submit} className="relative flex-1 max-w-[440px]">
        <Search className="size-4 absolute left-3 top-1/2 -translate-y-1/2 text-ink-3" />
        <input className="input pl-9 bg-sunken/70 border-transparent hover:border-line-strong" placeholder="Search incidents or jump to INC1042"
               value={q} onChange={e => setQ(e.target.value)} aria-label="Search incidents" />
      </form>
      <div className="ml-auto flex items-center gap-2">
        <span className="hidden sm:inline-flex items-center gap-2 text-[12px] font-semibold text-ink-3 px-2" title={connected ? 'Receiving live updates' : 'Reconnecting…'}>
          <span className={cx('size-2 rounded-full', connected ? 'bg-ok live-dot' : 'bg-line-strong')} />
          {connected ? 'Live' : 'Offline'}
        </span>
        <Notifications />
        {user.role !== 'ADMIN' && <NavLink to="/report" className="btn-primary hidden sm:inline-flex"><Plus className="size-4" />Report incident</NavLink>}
      </div>
    </header>
  )
}

function Notifications() {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const nav = useNavigate()
  const now = useNow()
  const qc = useQueryClient()
  const { data } = useQuery({ queryKey: ['notifications'], queryFn: () => api<{ items: NotificationView[]; unread: number }>('/notifications'), refetchInterval: 60_000 })
  const readAll = useMutation({ mutationFn: () => post('/notifications/read-all'), onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }) })
  const readOne = useMutation({ mutationFn: (id: number) => post(`/notifications/${id}/read`), onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }) })

  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false) }
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', close); document.addEventListener('keydown', esc)
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', esc) }
  }, [open])

  const unread = data?.unread ?? 0
  return (
    <div className="relative" ref={ref}>
      <button className="btn-ghost relative w-9 px-0" onClick={() => setOpen(o => !o)} aria-label={`Notifications, ${unread} unread`} aria-expanded={open}>
        <Bell className="size-[18px]" />
        {unread > 0 && <span className="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] px-1 rounded-full bg-p1 text-white text-[10.5px] font-bold grid place-items-center mono">{unread > 99 ? '99+' : unread}</span>}
      </button>
      {open && (
        <div className="fixed sm:absolute right-2 sm:right-0 top-16 sm:top-11 w-[min(400px,calc(100vw-16px))] panel shadow-[0_18px_40px_-12px_rgba(15,35,65,.3)] z-50">
          <div className="panel-head">
            <span className="panel-title">Notifications</span>
            <button className="btn-ghost btn-sm" disabled={unread === 0 || readAll.isPending} onClick={() => readAll.mutate()}>Mark all read</button>
          </div>
          <ul className="max-h-[420px] overflow-y-auto divide-y divide-line">
            {(data?.items ?? []).length === 0 && <li className="p-6 text-center text-ink-3 text-[13px]">You're all caught up. Updates on your incidents will appear here.</li>}
            {data?.items.map(n => (
              <li key={n.id}>
                <button className={cx('w-full text-left px-4 py-3 hover:bg-sunken flex gap-3', !n.read && 'bg-action-soft/50')}
                        onClick={() => { if (!n.read) readOne.mutate(n.id); if (n.incidentId) nav(`/incidents/${n.incidentId}`); setOpen(false) }}>
                  <span className={cx('mt-1.5 size-2 rounded-full shrink-0', n.read ? 'bg-transparent' : /SLA|Escalat|Major/.test(n.title) ? 'bg-p1' : 'bg-action')} />
                  <span className="min-w-0">
                    <span className="block text-[13px] font-semibold text-ink">{n.title}</span>
                    <span className="block text-[12.5px] text-ink-3 line-clamp-2">{n.message}</span>
                    <span className="block text-[11.5px] text-ink-3 mt-0.5">{ago(n.createdAt, now)}</span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

