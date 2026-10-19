/**
 * The HTTP layer. Nothing above this file knows about fetch, headers or
 * status codes — it only sees resolved data or a thrown error carrying
 * `status`, or `conflict` for the 409 that optimistic locking produces.
 *
 * Paths are relative on purpose: Vite proxies /api and /auth to Spring Boot,
 * so the browser stays same-origin and the refresh cookie is sent.
 */

let accessToken = null;

export const setAccessToken = (t) => { accessToken = t; };
export const getAccessToken = () => accessToken;

export async function http(path, options = {}) {
  const res = await fetch(path, {
    ...options,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...(options.headers || {})
    }
  });

  // 409 is its own case: the row moved under us and the UI shows a
  // reconciliation strip rather than a generic failure.
  if (res.status === 409) {
    throw Object.assign(new Error('conflict'), { conflict: true, status: 409 });
  }
  if (!res.ok) {
    throw Object.assign(new Error(`HTTP ${res.status}`), { status: res.status });
  }
  return res.status === 204 ? null : res.json();
}

/** Mock-mode helpers. Both disappear when USE_LIVE_API is true. */
export const wait = (ms) => new Promise((r) => setTimeout(r, ms));
export const clone = (v) => JSON.parse(JSON.stringify(v));
