import { useState, useRef } from 'react';
import * as api from '../../api/index.js';
import { initials } from '../../lib/roles.js';
import { fmtDate } from '../../lib/dates.js';

/**
 * Downscales and center-crops an uploaded image file onto a 256×256 canvas
 * and returns a compact JPEG data URL suitable for storing in users.avatar_url.
 */
function resizeImageToDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error('Could not read image file.'));
    reader.onload = () => {
      const img = new Image();
      img.onerror = () => reject(new Error('Invalid image file.'));
      img.onload = () => {
        const size = 256;
        const canvas = document.createElement('canvas');
        canvas.width = size;
        canvas.height = size;
        const ctx = canvas.getContext('2d');
        const minSide = Math.min(img.width, img.height);
        const sx = (img.width - minSide) / 2;
        const sy = (img.height - minSide) / 2;
        ctx.drawImage(img, sx, sy, minSide, minSide, 0, 0, size, size);
        resolve(canvas.toDataURL('image/jpeg', 0.86));
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  });
}

export default function ProfileSheet({ me, projects, tasks, onClose, onSaved }) {
  const [username, setUsername] = useState(me.username || '');
  const [bio, setBio] = useState(me.bio || '');
  const [avatarUrl, setAvatarUrl] = useState(me.avatarUrl || '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const fileInputRef = useRef(null);

  const ledCount = projects.filter((p) => p.role === 'LEADER' || p.role === 'MANAGER').length;
  const memberCount = projects.length - ledCount;
  const myOpenTasks = tasks.filter((t) => t.assigneeId === me.id && !t.done).length;
  const myDoneTasks = tasks.filter((t) => t.assigneeId === me.id && t.done).length;

  async function handleFileSelect(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      setError('Please select a valid image file (PNG, JPG, WebP).');
      return;
    }
    setError(null);
    try {
      const dataUrl = await resizeImageToDataUrl(file);
      setAvatarUrl(dataUrl);
    } catch (err) {
      setError(err.message || 'Could not process image.');
    } finally {
      e.target.value = '';
    }
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (!username.trim()) {
      setError('Display name cannot be blank.');
      return;
    }
    setError(null);
    setBusy(true);
    try {
      const updated = await api.updateProfile({
        username: username.trim(),
        bio: bio.trim() || null,
        avatarUrl: avatarUrl || null
      });
      onSaved(updated);
    } catch (err) {
      setError(err.detail || 'Could not save profile changes.');
      setBusy(false);
    }
  }

  return (
    <div className="scrim center" onClick={onClose}>
      <div className="sheet" style={{ maxWidth: '500px' }} role="dialog" aria-modal="true"
           aria-label="Profile details" onClick={(e) => e.stopPropagation()}>
        <div className="sheet-head">
          <h2>Profile details</h2>
          <p>View your account details, upload a profile photo, and update your display info.</p>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="sheet-body" style={{ maxHeight: '62vh' }}>
            {error && <div className="banner warn" role="alert">{error}</div>}

            <div className="profile-hero">
              <div className="profile-avatar-lg">
                {avatarUrl ? (
                  <img src={avatarUrl} alt={username || me.username} />
                ) : (
                  <span>{initials(username || me.username)}</span>
                )}
              </div>

              <div className="profile-photo-actions">
                <div style={{ fontWeight: 500, fontSize: '14px' }}>{username || me.username}</div>
                <div style={{ fontSize: '12px', color: 'var(--txt-mute)', marginBottom: '8px' }}>
                  {me.email}
                </div>
                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    hidden
                    onChange={handleFileSelect}
                  />
                  <button
                    type="button"
                    className="btn btn-cta"
                    style={{ padding: '6px 11px', fontSize: '12px' }}
                    onClick={() => fileInputRef.current?.click()}
                  >
                    {avatarUrl ? 'Change photo' : '+ Upload photo'}
                  </button>
                  {avatarUrl && (
                    <button
                      type="button"
                      className="btn btn-danger"
                      style={{ padding: '6px 11px', fontSize: '12px' }}
                      onClick={() => setAvatarUrl('')}
                    >
                      Remove photo
                    </button>
                  )}
                </div>
              </div>
            </div>

            <div className="field">
              <label htmlFor="prof-username">Display name</label>
              <input
                id="prof-username"
                value={username}
                maxLength={30}
                onChange={(e) => setUsername(e.target.value)}
                required
              />
            </div>

            <div className="field">
              <label htmlFor="prof-bio">Bio / Role title</label>
              <textarea
                id="prof-bio"
                value={bio}
                maxLength={500}
                onChange={(e) => setBio(e.target.value)}
                placeholder="e.g. Backend Engineer · Leading platform architecture"
                style={{ minHeight: '68px' }}
              />
            </div>

            <div className="profile-meta-box">
              <span className="mono" style={{ display: 'block', marginBottom: '8px' }}>
                Account & workspace summary
              </span>
              <dl className="kv" style={{ fontSize: '12.5px' }}>
                <dt>Email</dt>
                <dd>{me.email}</dd>

                {me.createdAt && (
                  <>
                    <dt>Joined</dt>
                    <dd className="mono" style={{ textTransform: 'none' }}>{fmtDate(me.createdAt)}</dd>
                  </>
                )}

                <dt>Projects</dt>
                <dd>{ledCount} led · {memberCount} member</dd>

                <dt>My tasks</dt>
                <dd>{myOpenTasks} open · {myDoneTasks} completed</dd>
              </dl>
            </div>
          </div>

          <div className="sheet-foot">
            <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
            <button
              type="submit"
              className="btn btn-primary"
              style={{ width: 'auto', marginTop: 0 }}
              disabled={busy}
            >
              {busy ? 'Saving…' : 'Save profile'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
