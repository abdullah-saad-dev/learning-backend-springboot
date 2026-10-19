/**
 * The single swap point.
 *
 * REAL — the request is written and correct against the current backend.
 * MOCK — names the endpoint it is waiting for and the shape it expects back.
 *
 * Turning a MOCK into a REAL is a one-function edit. Nothing outside this
 * folder knows which is which.
 */
import { USE_LIVE_API } from '../config.js';
import { http, setAccessToken, wait, clone } from './client.js';
import { FIXTURES } from './fixtures.js';

/* ── Auth ───────────────────────────────────────────────────────────── */

/** REAL — POST /auth/login → { accessToken, issuedAt, expiresAt }, plus the HttpOnly cookie. */
export async function login(email, password) {
  if (!USE_LIVE_API) { await wait(420); return clone(FIXTURES.me); }
  const jwt = await http('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password })
  });
  setAccessToken(jwt.accessToken);
  return me();
}

/** REAL — POST /auth/signup → 200, empty body. 409 means the email is taken. */
export async function signup(username, email, password) {
  if (!USE_LIVE_API) { await wait(520); return clone(FIXTURES.me); }
  await http('/auth/signup', {
    method: 'POST',
    body: JSON.stringify({ username, email, password })
  });
  return login(email, password);
}

/** REAL — POST /auth/refresh reads the cookie and returns a fresh JWT. */
export async function refresh() {
  if (!USE_LIVE_API) return clone(FIXTURES.me);
  const jwt = await http('/auth/refresh', { method: 'POST' });
  setAccessToken(jwt.accessToken);
  return me();
}

/**
 * MOCK — needs GET /auth/me → { id, username, email, role }
 * The JWT carries sub and role but no display name, so there is no way to
 * show who is signed in without this.
 */
export async function me() { await wait(120); return clone(FIXTURES.me); }

/**
 * MOCK — needs POST /auth/logout
 * Revokes the family behind the current refresh cookie and clears it with
 * Max-Age=0. RefreshTokenRepository.revokeFamily is already the revoking half.
 */
export async function logout() { await wait(200); return { ok: true }; }

/** MOCK — needs POST /auth/logout-all: every family for the user, not just this one. */
export async function logoutEverywhere() { await wait(320); return { ok: true }; }

/**
 * MOCK — needs GET /auth/sessions → [{ familyId, device, ip, lastSeenAt, current }]
 * One row per refresh-token family. The rows already exist in refresh_tokens;
 * nothing exposes them.
 */
export async function listSessions() { await wait(240); return clone(FIXTURES.sessions); }

/* ── Tasks ──────────────────────────────────────────────────────────── */

/**
 * REAL — GET /api/tasks?title=&done=
 * Returns [{ id, title, details, createdAt, done, version }] — note there is
 * no projectId and no assignee on the response yet.
 */
export async function listTasks({ title, done } = {}) {
  if (!USE_LIVE_API) { await wait(260); return clone(FIXTURES.tasks); }
  const q = new URLSearchParams();
  if (title) q.set('title', title);
  if (done !== undefined && done !== null) q.set('done', String(done));
  return http(`/api/tasks?${q.toString()}`);
}

/** REAL — POST /api/tasks { title, details, done } */
export async function createTask(body) {
  if (!USE_LIVE_API) {
    await wait(240);
    return { id: crypto.randomUUID(), createdAt: new Date().toISOString(), version: 0, ...body };
  }
  return http('/api/tasks', { method: 'POST', body: JSON.stringify(body) });
}

/** REAL — PATCH /api/tasks/{id}/done { done, version }. Sending the version is what makes a 409 possible. */
export async function setDone(id, done, version) {
  if (!USE_LIVE_API) { await wait(180); return { ok: true }; }
  return http(`/api/tasks/${id}/done`, {
    method: 'PATCH',
    body: JSON.stringify({ done, version })
  });
}

/** REAL — PUT /api/tasks/{id} { title, details, done, version } */
export async function updateTask(id, body) {
  if (!USE_LIVE_API) { await wait(200); return { ok: true }; }
  return http(`/api/tasks/${id}`, { method: 'PUT', body: JSON.stringify(body) });
}

/** REAL — DELETE /api/tasks/{id}?version= */
export async function deleteTask(id, version) {
  if (!USE_LIVE_API) { await wait(180); return null; }
  return http(`/api/tasks/${id}?version=${version}`, { method: 'DELETE' });
}

/* ── Projects ───────────────────────────────────────────────────────── */

/**
 * REAL — GET /api/projects
 * Returns [{ id, name, description, role, status, priority, memberCount,
 *            startDate, dueDate, version, createdAt }]
 */
export async function listProjects() {
  if (!USE_LIVE_API) { await wait(300); return clone(FIXTURES.projects); }
  return http('/api/projects');
}

/**
 * REAL — GET /api/projects/{id}/members → [{ id, username, email, role }]
 */
export async function listMembers(projectId) {
  if (!USE_LIVE_API) {
    await wait(160);
    return clone(FIXTURES.members[projectId] || []);
  }
  return http(`/api/projects/${projectId}/members`);
}

/**
 * MOCK — needs GET /api/projects/{id}/activity
 * The audit_logs table (V8) already has these columns:
 * [{ id, action, entityType, entityId, actorId, changes, createdAt }]
 */
export async function listActivity() { await wait(200); return []; }
