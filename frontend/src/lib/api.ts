// Typed client for the Spring Boot API. Errors arrive as RFC 7807 problem+json.

export type Role = 'EMPLOYEE' | 'TECHNICIAN' | 'ADMIN'
export type Priority = 'P1' | 'P2' | 'P3' | 'P4'
export type Status = 'NEW' | 'CLASSIFIED' | 'ASSIGNED' | 'ACKNOWLEDGED' | 'IN_PROGRESS' | 'AWAITING_INFO' | 'RESOLVED' | 'VERIFIED' | 'CLOSED' | 'CANCELLED'
export type SlaStatus = 'ON_TRACK' | 'AT_RISK' | 'BREACHED' | 'MET' | 'PAUSED'
export type Impact = 'LOW' | 'MEDIUM' | 'HIGH' | 'EXTENSIVE'
export type Urgency = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type Ref = { id: number; name: string }

export interface Profile { id: number; code: string; fullName: string; email: string; role: Role; department?: string; phone?: string; itManager: boolean }

export interface Summary {
  id: number; number: string; title: string; status: Status; priority?: Priority
  category?: Ref; service?: Ref; assignee?: Ref; reporter?: Ref
  slaStatus: SlaStatus; breached: boolean; escalationLevel: string
  responseDueAt?: string; resolutionDueAt?: string; createdAt: string; updatedAt?: string; duplicateOfNumber?: string
}
export interface Comment { id: number; author: string; authorRole: Role; type: string; body: string; internal: boolean; createdAt: string }
export interface Person { id: number; name: string; email: string; department?: string; phone?: string }
export interface Detail {
  summary: Summary; description: string; impact: Impact; urgency: Urgency; affectedUsers: number
  priorityScore: number; priorityReason?: string; autoCategorized: boolean; categoryConfidence?: number
  slaPolicyName?: string; responseTargetMinutes: number; resolutionTargetMinutes: number
  responseBreached: boolean; resolutionBreached: boolean; responseElapsed: number; resolutionElapsed: number
  acknowledgedAt?: string; resolvedAt?: string; closedAt?: string
  resolution?: string; rootCause?: string; closureNotes?: string; feedbackRating?: number; feedbackComment?: string
  reporter: Person; assignee?: Person; duplicateOf?: { id: number; number: string; title: string; score?: number }
  comments: Comment[]; allowedActions: string[]; version: number
}
export interface AuditView { id: number; timestamp: string; actor: string; action: string; incidentId?: number; incidentNumber?: string; fromState?: Status; toState?: Status; description: string }
export interface Recommendation { technicianId: number; name: string; score: number; reason: string; activeLoad: number; maxWorkload: number; experienceYears: number; skills: string[] }
export interface Similar { id: number; number: string; title: string; status: Status; service: string; score: number; resolution?: string; assignee?: string; createdAt: string }
export interface Page<T> { content: T[]; totalElements: number; totalPages: number; number: number; size: number }

export interface ServiceView { id: number; name: string; description?: string; criticality: string; slaPolicy?: Ref; defaultCategory?: Ref; userBase: number; active: boolean }
export interface CategoryView { id: number; name: string; description?: string; keywords: string[]; active: boolean }
export interface Catalog { services: ServiceView[]; categories: CategoryView[]; impacts: Impact[]; urgencies: Urgency[] }
export interface Analysis {
  categories: { categoryId: number; name: string; confidence: number; matchedKeywords: string[] }[]
  priority?: { priority: Priority; score: number; explanation: string }
  sla?: { policy: string; responseMinutes: number; resolutionMinutes: number }
  similar: Similar[]; knownFixes: Similar[]
}
export interface UserView {
  id: number; code: string; fullName: string; email: string; role: Role; department?: string; phone?: string; active: boolean; createdAt: string
  skills?: Ref[]; maxWorkload?: number; experienceYears?: number; available?: boolean; teamLead?: boolean; activeLoad?: number; itManager?: boolean
}
export interface SlaView { id: number; name: string; description?: string; defaultPolicy: boolean; targets: Record<Priority, { responseMinutes: number; resolutionMinutes: number }> }
export interface SettingsView { assignmentStrategy: string; slaWarningThreshold: number; escalationEnabled: boolean; autoAssign: boolean; strategies: { key: string; name: string; description: string; active: boolean }[] }
export interface NotificationView { id: number; type: string; title: string; message: string; incidentId?: number; read: boolean; createdAt: string }
export interface OutboxView { id: number; channel: 'EMAIL' | 'SMS'; recipient: string; type: string; title: string; message: string; incidentId?: number; externalId?: string; createdAt: string }

export interface Dashboard {
  overview: { total: number; open: number; criticalOpen: number; resolved: number; breached: number; atRisk: number; unassigned: number; mttrMinutes: number; slaCompliancePct: number; avgRating: number; ratings: number }
  byStatus: Count[]; byPriority: Count[]; byCategory: Count[]
  byService: { service: string; total: number; open: number; breached: number }[]
  technicians: { id: number; name: string; active: number; max: number; utilisation: number; resolved: number; avgRating?: number; breaches: number; available: boolean; skills: string[] }[]
  trend: { day: string; created: number; resolved: number; breached: number }[]
  mttrByPriority: Count[]
  serviceHealth: { serviceId: number; service: string; criticality: string; open: number; recent: number; critical: number; health: 'HEALTHY' | 'DEGRADED' | 'OUTAGE' }[]
  clusters: { rootId: number; rootNumber: string; title: string; service: string; members: string[] }[]
  slaRisk: { id: number; number: string; title: string; priority: Priority; assignee?: string; risk: number; reasons: string[]; resolutionDueAt: string }[]
}
export type Count = { label: string; value: number }
export interface MyStats { active: number; resolvedThisWeek: number; avgRating?: number; breaches: number; maxWorkload: number; byStatus: Count[] }

export class ApiError extends Error {
  constructor(public status: number, message: string, public code?: string, public fields?: Record<string, string>) { super(message) }
}

const TOKEN_KEY = 'itsm.token'
export const tokenStore = {
  get: () => { try { return localStorage.getItem(TOKEN_KEY) } catch { return null } },
  set: (t: string | null) => { try { t ? localStorage.setItem(TOKEN_KEY, t) : localStorage.removeItem(TOKEN_KEY) } catch { /* private mode */ } },
}

let onUnauthorized: () => void = () => {}
export const setUnauthorizedHandler = (fn: () => void) => { onUnauthorized = fn }

export async function api<T>(path: string, init: RequestInit & { json?: unknown } = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const token = tokenStore.get()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init.json !== undefined) headers.set('Content-Type', 'application/json')
  let res: Response
  try {
    res = await fetch(`/api${path}`, { ...init, headers, body: init.json !== undefined ? JSON.stringify(init.json) : init.body })
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.', 'NETWORK')
  }
  if (res.status === 401 && token && !path.startsWith('/auth/login')) onUnauthorized()
  if (!res.ok) {
    let body: { detail?: string; code?: string; fields?: Record<string, string> } = {}
    try { body = await res.json() } catch { /* not json */ }
    throw new ApiError(res.status, body.detail ?? `Request failed (${res.status})`, body.code, body.fields)
  }
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const post = <T>(path: string, json?: unknown) => api<T>(path, { method: 'POST', json: json ?? {} })
export const put = <T>(path: string, json: unknown) => api<T>(path, { method: 'PUT', json })
export const patch = <T>(path: string, json: unknown) => api<T>(path, { method: 'PATCH', json })

export function qs(params: Record<string, unknown>): string {
  const p = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '' || (Array.isArray(v) && v.length === 0)) continue
    if (Array.isArray(v)) v.forEach(x => p.append(k, String(x)))
    else p.set(k, String(v))
  }
  const s = p.toString()
  return s ? `?${s}` : ''
}
