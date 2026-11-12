import { lazy, Suspense, type ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './lib/auth'
import type { Role } from './lib/api'
import { Shell } from './components/Shell'
import { Spinner } from './components/ui'
import Login from './pages/Login'

const MyIncidents = lazy(() => import('./pages/MyIncidents'))
const Report = lazy(() => import('./pages/Report'))
const IncidentDetail = lazy(() => import('./pages/IncidentDetail'))
const Queue = lazy(() => import('./pages/Queue'))
const Incidents = lazy(() => import('./pages/Incidents'))
const Dashboard = lazy(() => import('./pages/Dashboard'))
const Admin = lazy(() => import('./pages/Admin'))

const HOME: Record<Role, string> = { EMPLOYEE: '/my', TECHNICIAN: '/queue', ADMIN: '/dashboard' }

function Guard({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (roles && !roles.includes(user.role)) return <Navigate to={HOME[user.role]} replace />
  return <>{children}</>
}

export default function App() {
  const { user, ready } = useAuth()
  if (!ready) return <div className="min-h-dvh grid place-items-center text-ink-3"><Spinner className="size-6" /></div>
  if (!user) {
    return (
      <Routes>
        <Route path="/register" element={<Login mode="register" />} />
        <Route path="*" element={<Login mode="login" />} />
      </Routes>
    )
  }
  return (
    <Shell>
      <Suspense fallback={<div className="py-24 grid place-items-center text-ink-3"><Spinner className="size-6" /></div>}>
        <Routes>
          <Route path="/my" element={<Guard roles={['EMPLOYEE']}><MyIncidents /></Guard>} />
          <Route path="/report" element={<Guard><Report /></Guard>} />
          <Route path="/incidents/:id" element={<Guard><IncidentDetail /></Guard>} />
          <Route path="/queue" element={<Guard roles={['TECHNICIAN']}><Queue /></Guard>} />
          <Route path="/incidents" element={<Guard roles={['TECHNICIAN', 'ADMIN']}><Incidents /></Guard>} />
          <Route path="/dashboard" element={<Guard roles={['ADMIN']}><Dashboard /></Guard>} />
          <Route path="/admin/:section" element={<Guard roles={['ADMIN']}><Admin /></Guard>} />
          <Route path="*" element={<Navigate to={HOME[user.role]} replace />} />
        </Routes>
      </Suspense>
    </Shell>
  )
}
