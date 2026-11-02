/**
 * The HTTP layer. Nothing above this file knows about fetch, headers or
 * status codes — it only sees resolved data or a thrown error carrying
 * `status`, `detail` (from RFC 7807 ProblemDetail), or `conflict` for the 409
 * that optimistic locking produces.
 *
 * Paths are relative on purpose: Vite proxies /api and /auth to Spring Boot,
 * so the browser stays same-origin and the refresh cookie is sent.
 */

let accessToken = null;
let refreshInFlight = null;

export const setAccessToken = (t) => { accessToken = t; };
export const getAccessToken = () => accessToken;

async function tryRefreshToken() {
  if (!refreshInFlight) {
    refreshInFlight = fetch('/auth/refresh', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' }
    })
      .then(async (res) => {
        if (!res.ok) throw new Error('refresh failed');
        const jwt = await res.json();
        setAccessToken(jwt.accessToken);
        return jwt.accessToken;
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

export async function http(path, options = {}, retried = false) {
  const res = await fetch(path, {
    ...options,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...(options.headers || {})
    }
  });

  // Transparently refresh once if an authenticated call gets 401
  if (
    res.status === 401 &&
    !retried &&
    path !== '/auth/login' &&
    path !== '/auth/signup' &&
    path !== '/auth/otp/send' &&
    path !== '/auth/otp/verify' &&
    path !== '/auth/refresh' &&
    path !== '/auth/logout'
  ) {
    try {
      await tryRefreshToken();
      return http(path, options, true);
    } catch {
      setAccessToken(null);
    }
  }

  if (res.status === 204) {
    return null;
  }

  let body = null;
  const contentType = res.headers.get('content-type') || '';
  if (contentType.includes('json')) {
    try {
      body = await res.json();
    } catch {
      body = null;
    }
  }

  const detail = body?.detail || body?.title || null;

  // 409 is its own case: the row moved under us and the UI shows a
  // reconciliation strip rather than a generic failure.
  if (res.status === 409) {
    throw Object.assign(new Error(detail || 'conflict'), {
      conflict: true,
      status: 409,
      detail
    });
  }
  if (!res.ok) {
    throw Object.assign(new Error(detail || `HTTP ${res.status}`), {
      status: res.status,
      detail
    });
  }
  return body;
}

/** Mock-mode helpers. Both disappear when USE_LIVE_API is true. */
export const wait = (ms) => new Promise((r) => setTimeout(r, ms));
export const clone = (v) => JSON.parse(JSON.stringify(v));
