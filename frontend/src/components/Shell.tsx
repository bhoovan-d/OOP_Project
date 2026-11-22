import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Bell, BookUser, ClipboardList, FileClock, Gauge, Inbox, LayoutGrid, ListChecks, LogOut, Menu, Plus,
  Search, Send, Server, Settings2, ShieldCheck, Tags, Timer, X,
} from 'lucide-react'
import { api, post, qs, type NotificationView, type Page, type Role, type Summary } from '../lib/api'
import { useAuth, useUser } from '../lib/auth'
import { useLive, useNow } from '../lib/live'
import { ago } from '../lib/format'
import { cx } from './ui'

type NavItem = { to: string; label: string; icon: ReactNode; count?: boolean }
const I = 'size-[17px]'

const NAV: Record<Role, { section?: string; items: NavItem[] }[]> = {
  EMPLOYEE: [{ items: [
    { to: '/my', label: 'My incidents', icon: <ClipboardList className={I} />, count: true },
    { to: '/report', label: 'Report an incident', icon: <Plus className={I} /> },
  ] }],
  TECHNICIAN: [{ items: [
    { to: '/queue', label: 'My queue', icon: <Inbox className={I} />, count: true },
    { to: '/incidents?unassigned=true', label: 'Unassigned', icon: <ListChecks className={I} /> },
    { to: '/incidents', label: 'All incidents', icon: <LayoutGrid className={I} /> },
    { to: '/report', label: 'Log an incident', icon: <Plus className={I} /> },
  ] }],
  ADMIN: [
    { items: [
      { to: '/dashboard', label: 'Operations', icon: <Gauge className={I} /> },
      { to: '/incidents', label: 'Incidents', icon: <LayoutGrid className={I} />, count: true },
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
    <div className="min-h-dvh lg:grid lg:grid-cols-[232px_1fr]">
      <aside className={cx('fixed inset-y-0 left-0 z-40 w-[232px] bg-board text-chalk flex flex-col transition-transform duration-150 lg:sticky lg:top-0 lg:h-dvh lg:translate-x-0',
        open ? 'translate-x-0' : '-translate-x-full')}>
        <div className="flex items-center gap-3 px-4 h-[60px] border-b border-board-line">
          <Logo />
          <div className="leading-tight min-w-0">
            <div className="text-[12.5px] font-semibold text-chalk">{ROLE_NAME[user.role]}</div>
          </div>
          <button className="ml-auto lg:hidden text-chalk p-1" onClick={() => setOpen(false)} aria-label="Close menu"><X className="size-5" /></button>
        </div>
        <nav className="flex-1 overflow-y-auto px-3 py-5 space-y-6" onClick={() => setOpen(false)}>
          {NAV[user.role].map((g, gi) => (
            <div key={gi}>
              {g.section && <div className="px-3 mb-2 text-[11px] font-semibold uppercase tracking-[0.14em] text-chalk/70">{g.section}</div>}
              <ul className="space-y-0.5">
                {g.items.map(it => <li key={it.to}><RailLink {...it} /></li>)}
              </ul>
            </div>
          ))}
        </nav>
        <UserCard />
      </aside>
      {open && <div className="fixed inset-0 z-30 bg-board/50 lg:hidden" onClick={() => setOpen(false)} />}
      <div className="min-w-0 flex flex-col">
        <TopBar onMenu={() => setOpen(true)} />
        <main className="flex-1 px-4 sm:px-6 lg:px-8 py-6 max-w-[1480px] w-full mx-auto">{children}</main>
      </div>
    </div>
  )
}

function RailLink({ to, label, icon, count }: NavItem) {
  const loc = useLocation()
  const [path, query] = to.split('?')
  const active = loc.pathname === path
    ? (query ? loc.search.includes(query) : !/unassigned|breached/.test(loc.search))
    : !query && loc.pathname.startsWith(path + '/')
  return (
    <Link to={to} aria-current={active ? 'page' : undefined}
      className={cx('flex items-center gap-3 px-3 h-10 rounded-[3px] text-[14.5px] font-semibold transition-colors',
        active ? 'plate text-ink before:hidden after:hidden' : 'text-chalk hover:bg-board-2 hover:text-white')}>
      {icon}<span className="flex-1 truncate">{label}</span>{count && <NavCount active={active} />}
    </Link>
  )
}

function Logo() {
  return (
    <span className="plate inline-flex items-end h-10 px-3.5 pb-1 pt-3.5 font-display font-extrabold text-[21px] leading-none tracking-[0.01em] shrink-0">
      I-ITSM
    </span>
  )
}

/** Live count on the first nav item: open work for this role, hung as a small plate. */
function NavCount({ active }: { active: boolean }) {
  const user = useUser()
  const params = user.role === 'TECHNICIAN' ? { assigneeId: user.id, open: true, size: 1 } : { open: true, size: 1 }
  const { data } = useQuery({ queryKey: ['incidents', 'navcount', user.id], queryFn: () => api<Page<Summary>>(`/incidents${qs(params)}`), refetchInterval: 60_000 })
  if (!data) return null
  return (
    <span className={cx('font-display font-bold text-[15px] leading-none min-w-[26px] h-[22px] px-1.5 rounded-[2px] grid place-items-center tabular-nums',
      active ? 'bg-board text-white' : 'bg-plate text-ink')}>{data.totalElements}</span>
  )
}

function UserCard() {
  const user = useUser()
  const { logout } = useAuth()
  const initials = user.fullName.split(' ').map(s => s[0]).slice(0, 2).join('')
  return (
    <div className="border-t border-board-line p-3 flex items-center gap-3">
      <span className="size-9 rounded-[2px] bg-plate text-ink grid place-items-center font-display text-[17px] font-extrabold">{initials}</span>
      <div className="min-w-0 flex-1 leading-tight">
        <div className="text-white text-[14px] font-semibold truncate">{user.fullName}</div>
        <div className="text-[12px] text-chalk">{user.code}{user.itManager ? ' · IT manager' : ''}</div>
      </div>
      <button className="p-2 rounded-[3px] text-chalk hover:text-white hover:bg-board-2" onClick={logout} aria-label="Sign out" title="Sign out">
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
    <header className="sticky top-0 z-20 h-[60px] bg-surface border-b border-line flex items-center gap-3 px-4 sm:px-6 lg:px-8">
      <button className="lg:hidden btn-ghost btn-sm" onClick={onMenu} aria-label="Open menu"><Menu className="size-5" /></button>
      <form onSubmit={submit} className="relative flex-1 max-w-[440px]">
        <Search className="size-4 absolute left-3 top-1/2 -translate-y-1/2 text-ink-3" />
        <input className="input pl-9 bg-sunken border-line hover:border-line-strong" placeholder="Search incidents or jump to INC1042"
               value={q} onChange={e => setQ(e.target.value)} aria-label="Search incidents" />
      </form>
      <div className="ml-auto flex items-center gap-2">
        <span className="hidden sm:inline-flex items-center gap-2 text-[12px] font-semibold uppercase tracking-[0.12em] text-ink-2 px-2" title={connected ? 'Receiving live updates' : 'Reconnecting…'}>
          <span className={cx('size-2.5 rounded-full', connected ? 'bg-ok live-dot' : 'bg-line-strong')} />
          {connected ? 'Live' : 'Offline'}
        </span>
        <Notifications />
        <NavLink to="/report" className="btn-primary hidden sm:inline-flex"><Plus className="size-4" />Report incident</NavLink>
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
        {unread > 0 && <span className="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] px-1 rounded-[2px] bg-p1 text-white text-[12.5px] font-bold grid place-items-center mono">{unread > 99 ? '99+' : unread}</span>}
      </button>
      {open && (
        <div className="fixed sm:absolute right-2 sm:right-0 top-16 sm:top-11 w-[min(400px,calc(100vw-16px))] panel shadow-[0_18px_40px_-12px_rgba(8,30,22,.35)] z-50">
          <div className="panel-head">
            <span className="panel-title">Notifications</span>
            <button className="btn-ghost btn-sm" disabled={unread === 0 || readAll.isPending} onClick={() => readAll.mutate()}>Mark all read</button>
          </div>
          <ul className="max-h-[420px] overflow-y-auto divide-y divide-line">
            {(data?.items ?? []).length === 0 && <li className="p-6 text-center text-ink-3 text-[14px]">You're all caught up. Updates on your incidents will appear here.</li>}
            {data?.items.map(n => (
              <li key={n.id}>
                <button className={cx('w-full text-left px-4 py-3 hover:bg-sunken flex gap-3', !n.read && 'bg-action-soft/50')}
                        onClick={() => { if (!n.read) readOne.mutate(n.id); if (n.incidentId) nav(`/incidents/${n.incidentId}`); setOpen(false) }}>
                  <span className={cx('mt-1.5 size-2 rounded-full shrink-0', n.read ? 'bg-transparent' : /SLA|Escalat|Major/.test(n.title) ? 'bg-p1' : 'bg-action')} />
                  <span className="min-w-0">
                    <span className="block text-[14px] font-semibold text-ink">{n.title}</span>
                    <span className="block text-[13px] text-ink-3 line-clamp-2">{n.message}</span>
                    <span className="block text-[12px] text-ink-3 mt-0.5">{ago(n.createdAt, now)}</span>
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

