import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api, post, setUnauthorizedHandler, tokenStore, type Profile } from './api'

interface AuthState {
  user: Profile | null
  ready: boolean
  login: (email: string, password: string) => Promise<Profile>
  register: (data: { fullName: string; email: string; password: string; department?: string; phone?: string }) => Promise<Profile>
  logout: () => void
}

const Ctx = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Profile | null>(null)
  const [ready, setReady] = useState(false)
  const qc = useQueryClient()

  const logout = useCallback(() => {
    tokenStore.set(null)
    setUser(null)
    qc.clear()
  }, [qc])

  useEffect(() => {
    setUnauthorizedHandler(logout)
    if (!tokenStore.get()) { setReady(true); return }
    api<Profile>('/auth/me').then(setUser).catch(() => tokenStore.set(null)).finally(() => setReady(true))
  }, [logout])

  const accept = (r: { token: string; user: Profile }) => { tokenStore.set(r.token); setUser(r.user); return r.user }

  const value = useMemo<AuthState>(() => ({
    user, ready, logout,
    login: async (email, password) => accept(await post('/auth/login', { email, password })),
    register: async data => accept(await post('/auth/register', data)),
  }), [user, ready, logout])

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useAuth() {
  const c = useContext(Ctx)
  if (!c) throw new Error('useAuth outside AuthProvider')
  return c
}

export function useUser(): Profile {
  const { user } = useAuth()
  if (!user) throw new Error('No user')
  return user
}
