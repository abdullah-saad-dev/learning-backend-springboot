import { useState } from 'react';
import { PRODUCT } from '../../config.js';
import * as api from '../../api/index.js';

/**
 * Sign in and sign up are the same screen with one extra field, so they are
 * one component. Splitting them would duplicate the layout, the error
 * handling and the submit path to save a single conditional.
 */
export default function AuthScreen({ mode, onMode, onDone }) {
  const isSignup = mode === 'signup';
  const [form, setForm] = useState({ username: '', email: '', password: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const me = isSignup
        ? await api.signup(form.username, form.email, form.password)
        : await api.login(form.email, form.password);
      onDone(me);
    } catch (err) {
      setError(
        err.status === 401 ? 'That email and password do not match an account.'
        : err.status === 409 ? 'An account already uses that email.'
        : 'Could not reach the server. Check that Spring Boot is running on port 8080.'
      );
      setBusy(false);
    }
  }

  return (
    <div className="auth">
      <aside className="auth-aside">
        <div className="spine" aria-hidden="true" />

        <div className="wordmark"><i />{PRODUCT}</div>

        <div className="auth-pitch">
          <h1>You lead some of this. You are only along for the rest.</h1>
          <p>
            {PRODUCT} keeps that distinction in front of you. Every project you touch
            is marked by the authority you hold in it, on every screen.
          </p>
        </div>

        {/* The legend teaches the colour code before the first project is seen.
            Its bars sit on the spine — same device, same axis. */}
        <div className="legend">
          <div className="legend-row">
            <span className="legend-bar" style={{ background: 'var(--gold)' }} />
            <span><b>You lead it</b><span>Yours to plan, assign and close.</span></span>
          </div>
          <div className="legend-row">
            <span className="legend-bar" style={{ background: 'var(--blue)' }} />
            <span><b>You are in it</b><span>Yours to contribute to.</span></span>
          </div>
        </div>
      </aside>

      <main className="auth-main">
        <div className="auth-card">
          <h2>{isSignup ? 'Create your account' : 'Sign in'}</h2>
          <p className="sub">
            {isSignup
              ? 'One account covers every project you lead or join.'
              : 'Welcome back.'}
          </p>

          {error && <div className="banner warn" role="alert">{error}</div>}

          <form onSubmit={submit} noValidate>
            {isSignup && (
              <div className="field">
                <label htmlFor="username">Name</label>
                <input id="username" value={form.username} onChange={set('username')}
                       autoComplete="name" placeholder="Abdullah Saad" required />
              </div>
            )}

            <div className="field">
              <label htmlFor="email">Email</label>
              <input id="email" type="email" value={form.email} onChange={set('email')}
                     autoComplete="email" placeholder="you@example.com" required />
            </div>

            <div className="field">
              <label htmlFor="password">Password</label>
              <input id="password" type="password" value={form.password} onChange={set('password')}
                     autoComplete={isSignup ? 'new-password' : 'current-password'}
                     placeholder="Your password" required />
              {isSignup && (
                <p className="hint">
                  The API accepts any non-blank password today. A length rule belongs
                  here once it exists.
                </p>
              )}
            </div>

            <button className="btn btn-primary" type="submit" disabled={busy}>
              {busy
                ? (isSignup ? 'Creating account…' : 'Signing in…')
                : (isSignup ? 'Create account' : 'Sign in')}
            </button>
          </form>

          <p className="auth-swap">
            {isSignup ? 'Already have an account? ' : 'No account yet? '}
            <button type="button" onClick={() => onMode(isSignup ? 'login' : 'signup')}>
              {isSignup ? 'Sign in' : 'Create one'}
            </button>
          </p>
        </div>
      </main>
    </div>
  );
}
