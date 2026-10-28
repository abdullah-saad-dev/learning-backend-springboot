import { useState, useEffect } from 'react';
import * as api from '../../api/index.js';
import { isLead } from '../../lib/roles.js';

export default function MembersSheet({ project, me, onClose, onChanged, onDeleted }) {
  const canManage = isLead(project?.role);
  const [members, setMembers] = useState([]);
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('MEMBER');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!project) return;
    let live = true;
    api.listMembers(project.id)
      .then((m) => { if (live) setMembers(m); })
      .catch((err) => { if (live) setError(err.detail || 'Could not load members.'); });
    return () => { live = false; };
  }, [project]);

  async function handleAdd(e) {
    e.preventDefault();
    if (!email.trim()) return;
    setError(null);
    setBusy(true);
    try {
      const added = await api.addMember(project.id, { email: email.trim(), role });
      setMembers((prev) => {
        const filtered = prev.filter((x) => x.id !== added.id);
        return [...filtered, added];
      });
      setEmail('');
      onChanged();
    } catch (err) {
      setError(err.detail || 'Could not add member.');
    } finally {
      setBusy(false);
    }
  }

  async function handleRoleChange(memberId, newRole) {
    setError(null);
    try {
      const updated = await api.updateMemberRole(project.id, memberId, newRole);
      setMembers((prev) => prev.map((m) => (m.id === memberId ? updated : m)));
      onChanged();
    } catch (err) {
      setError(err.detail || 'Could not update role.');
    }
  }

  async function handleRemove(memberId) {
    setError(null);
    try {
      await api.removeMember(project.id, memberId);
      setMembers((prev) => prev.filter((m) => m.id !== memberId));
      onChanged();
    } catch (err) {
      setError(err.detail || 'Could not remove member.');
    }
  }

  async function handleDeleteProject() {
    setError(null);
    setBusy(true);
    try {
      await api.deleteProject(project.id, project.version ?? 0);
      onDeleted(project.id);
    } catch (err) {
      setError(err.detail || 'Could not delete project.');
      setBusy(false);
    }
  }

  return (
    <div className="scrim center" onClick={onClose}>
      <div className="sheet" style={{ maxWidth: '500px' }} role="dialog" aria-modal="true"
           aria-label="Project members" onClick={(e) => e.stopPropagation()}>
        <div className="sheet-head">
          <h2>{project.name} · Members</h2>
          <p>{project.description || 'Manage who belongs to this project and their authority.'}</p>
        </div>

        <div className="sheet-body">
          {error && <div className="banner warn" role="alert">{error}</div>}

          <div style={{ display: 'grid', gap: '8px', marginBottom: canManage ? '16px' : 0 }}>
            {members.map((m) => (
              <div key={m.id} style={{
                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                gap: '10px', padding: '8px 10px', background: 'var(--panel)',
                border: '1px solid var(--line)', borderRadius: 'var(--r-sm)'
              }}>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: '13px', fontWeight: 500 }}>
                    {m.username} {m.id === me?.id && <span className="mono">(you)</span>}
                  </div>
                  <div style={{ fontSize: '11.5px', color: 'var(--txt-mute)' }}>{m.email}</div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  {canManage ? (
                    <>
                      <select
                        value={m.role}
                        aria-label={`Role for ${m.username}`}
                        onChange={(e) => handleRoleChange(m.id, e.target.value)}
                        style={{
                          padding: '4px 8px', background: 'var(--raised)',
                          border: '1px solid var(--line-hi)', borderRadius: 'var(--r-sm)',
                          fontSize: '12px'
                        }}
                      >
                        <option value="MANAGER">Manager</option>
                        <option value="MEMBER">Member</option>
                      </select>
                      <button
                        type="button"
                        className="btn btn-danger"
                        style={{ padding: '4px 8px', fontSize: '12px' }}
                        onClick={() => handleRemove(m.id)}
                      >
                        Remove
                      </button>
                    </>
                  ) : (
                    <span className={`role-chip ${m.role === 'MANAGER' ? 'lead' : 'member'}`}>
                      <i />{m.role === 'MANAGER' ? 'Manager' : 'Member'}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>

          {canManage && (
            <form onSubmit={handleAdd} style={{
              borderTop: '1px solid var(--line)', paddingTop: '14px',
              display: 'grid', gridTemplateColumns: '1fr auto auto', gap: '8px', alignItems: 'end'
            }}>
              <div className="field" style={{ marginBottom: 0 }}>
                <label htmlFor="add-member-email">Add member by email</label>
                <input id="add-member-email" type="email" value={email}
                       onChange={(e) => setEmail(e.target.value)}
                       placeholder="colleague@example.com" required />
              </div>

              <div className="field" style={{ marginBottom: 0 }}>
                <label htmlFor="add-member-role">Role</label>
                <select id="add-member-role" value={role} onChange={(e) => setRole(e.target.value)}>
                  <option value="MEMBER">Member</option>
                  <option value="MANAGER">Manager</option>
                </select>
              </div>

              <button type="submit" className="btn btn-ghost"
                      style={{ height: '39px' }} disabled={busy}>
                Add
              </button>
            </form>
          )}
        </div>

        <div className="sheet-foot" style={{ justifyContent: 'space-between' }}>
          {canManage ? (
            <button type="button" className="btn btn-danger"
                    onClick={handleDeleteProject} disabled={busy}>
              Delete project
            </button>
          ) : <span />}
          <button type="button" className="btn btn-ghost" onClick={onClose}>Done</button>
        </div>
      </div>
    </div>
  );
}
