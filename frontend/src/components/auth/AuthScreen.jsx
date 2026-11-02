import { useState } from 'react';
import { PRODUCT } from '../../config.js';
import * as api from '../../api/index.js';

/**
 * Sign in and sign up share the same screen; sign up adds a 6-digit OTP email
 * verification step before creating the account.
 */
export default function AuthScreen({ mode, onMode, onDone }) {
  const isSignup = mode === 'signup';
  const [form, setForm] = useState({ username: '', email: '', password: '' });
  const [otpSent, setOtpSent] = useState(false);
  const [otp, setOtp] = useState('');
  const [info, setInfo] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));

  const hasMinLen = form.password.length >= 8;
  const hasNumber = /\d/.test(form.password);
  const hasSpecial = /[^A-Za-z0-9\s]/.test(form.password);
  const isPasswordValid = hasMinLen && hasNumber && hasSpecial;

  function switchMode(nextMode) {
    setError(null);
    setInfo(null);
    setOtpSent(false);
    setOtp('');
    onMode(nextMode);
  }

  async function submit(e) {
    e.preventDefault();
    setError(null);
    setInfo(null);

    if (!isSignup) {
      setBusy(true);
      try {
        const me = await api.login(form.email, form.password);
        onDone(me);
      } catch (err) {
        setError(
          err.status === 401 ? 'That email and password do not match an account.'
          : err.status === 400 ? (err.detail || 'Please check your email and password.')
          : 'Could not reach the server. Check that Spring Boot is running on port 8080.'
        );
        setBusy(false);
      }
      return;
    }

    if (!otpSent) {
      if (!form.username.trim() || !form.email.trim()) {
        setError('Please enter your name and a valid email address.');
        return;
      }
      if (!isPasswordValid) {
        setError('Password must be at least 8 characters and include at least 1 number and 1 special character.');
        return;
      }
      setBusy(true);
      try {
        await api.sendSignupOtp(form.email.trim());
        setOtpSent(true);
        setOtp('');
        setInfo(`A 6-digit verification code was sent to ${form.email.trim()}.`);
      } catch (err) {
        setError(
          err.status === 409 ? 'An account already uses that email.'
          : err.status === 400 ? (err.detail || 'Please enter a valid email address.')
          : 'Could not reach the server. Check that Spring Boot is running on port 8080.'
        );
      } finally {
        setBusy(false);
      }
      return;
    }

    if (!/^\d{6}$/.test(otp.trim())) {
      setError('Please enter the 6-digit verification code.');
      return;
    }

    setBusy(true);
    try {
      await api.verifySignupOtp(form.email.trim(), otp.trim());
      const me = await api.signup(form.username.trim(), form.email.trim(), form.password, otp.trim());
      onDone(me);
    } catch (err) {
      setError(
        err.status === 409 ? 'An account already uses that email.'
        : err.status === 400 ? (err.detail || 'Invalid or expired verification code.')
        : 'Could not reach the server. Check that Spring Boot is running on port 8080.'
      );
      setBusy(false);
    }
  }

  async function handleResendOtp() {
    setError(null);
    setInfo(null);
    setBusy(true);
    try {
      await api.sendSignupOtp(form.email.trim());
      setOtp('');
      setInfo(`Sent a fresh 6-digit code to ${form.email.trim()}.`);
    } catch (err) {
      setError(err.detail || 'Could not resend verification code.');
    } finally {
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
          <h2>
            {!isSignup
              ? 'Sign in'
              : otpSent
                ? 'Verify your email'
                : 'Create your account'}
          </h2>
          <p className="sub">
            {!isSignup
              ? 'Welcome back.'
              : otpSent
                ? `Enter the 6-digit code sent to ${form.email.trim()}.`
                : 'One account covers every project you lead or join.'}
          </p>

          {error && <div className="banner warn" role="alert">{error}</div>}
          {info && !error && (
            <div className="banner" style={{
              marginBottom: '14px',
              padding: '10px 12px',
              borderRadius: 'var(--r-sm)',
              border: '1px solid var(--line-hi)',
              background: 'var(--raised)',
              fontSize: '12.5px',
              color: 'var(--txt-dim)'
            }} role="status">
              {info}
            </div>
          )}

          <form onSubmit={submit} noValidate>
            {isSignup && otpSent ? (
              <>
                <div className="field">
                  <label htmlFor="otp">6-digit verification code</label>
                  <input
                    id="otp"
                    type="text"
                    inputMode="numeric"
                    pattern="\d{6}"
                    maxLength={6}
                    value={otp}
                    onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                    placeholder="000000"
                    autoFocus
                    style={{
                      fontFamily: 'var(--mono)',
                      fontSize: '18px',
                      letterSpacing: '0.28em',
                      textAlign: 'center'
                    }}
                    required
                  />
                  <p className="hint">
                    Code expires in 5 minutes.
                  </p>
                </div>

                <button className="btn btn-primary" type="submit" disabled={busy || otp.length !== 6}>
                  {busy ? 'Verifying & creating account…' : 'Verify & create account'}
                </button>

                <div style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  marginTop: '12px',
                  fontSize: '12.5px',
                  color: 'var(--txt-mute)'
                }}>
                  <button
                    type="button"
                    style={{ color: 'var(--txt-dim)', textDecoration: 'underline' }}
                    onClick={() => { setOtpSent(false); setError(null); setInfo(null); }}
                    disabled={busy}
                  >
                    ← Edit details
                  </button>
                  <button
                    type="button"
                    style={{ color: 'var(--gold)', textDecoration: 'underline' }}
                    onClick={handleResendOtp}
                    disabled={busy}
                  >
                    Resend code
                  </button>
                </div>
              </>
            ) : (
              <>
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
                         placeholder="Your password" minLength={isSignup ? 8 : undefined} required />
                  {isSignup && (
                    <p className="hint" style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                      <span style={{ color: hasMinLen ? 'var(--gold)' : 'var(--txt-mute)' }}>
                        {hasMinLen ? '✓' : '·'} 8+ characters
                      </span>
                      <span style={{ color: hasNumber ? 'var(--gold)' : 'var(--txt-mute)' }}>
                        {hasNumber ? '✓' : '·'} 1 number
                      </span>
                      <span style={{ color: hasSpecial ? 'var(--gold)' : 'var(--txt-mute)' }}>
                        {hasSpecial ? '✓' : '·'} 1 special character
                      </span>
                    </p>
                  )}
                </div>

                <button className="btn btn-primary" type="submit" disabled={busy}>
                  {busy
                    ? (isSignup ? 'Sending verification code…' : 'Signing in…')
                    : (isSignup ? 'Send verification code' : 'Sign in')}
                </button>
              </>
            )}
          </form>

          <p className="auth-swap">
            {isSignup ? 'Already have an account? ' : 'No account yet? '}
            <button type="button" onClick={() => switchMode(isSignup ? 'login' : 'signup')}>
              {isSignup ? 'Sign in' : 'Create one'}
            </button>
          </p>
        </div>
      </main>
    </div>
  );
}
