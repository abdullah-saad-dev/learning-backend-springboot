import { useState, useEffect } from 'react';
import * as api from '../../api/index.js';

export default function NewTaskSheet({ projects, defaultProjectId, onClose, onCreated }) {
  const initialProject = defaultProjectId || projects[0]?.id || '';
  const [form, setForm] = useState({
    projectId: initialProject,
    title: '',
    details: '',
    dueDate: '',
    assigneeId: ''
  });
  const [members, setMembers] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!form.projectId) {
      setMembers([]);
      return;
    }
    let live = true;
    api.listMembers(form.projectId)
      .then((m) => {
        if (!live) return;
        setMembers(m);
        setForm((f) => ({
          ...f,
          assigneeId: m.length === 1 ? m[0].id : ''
        }));
      })
      .catch(() => {
        if (live) setMembers([]);
      });
    return () => { live = false; };
  }, [form.projectId]);

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    if (!form.projectId || !form.title.trim()) {
      setError('Project and title are required.');
      return;
    }
    setError(null);
    setBusy(true);
    try {
      const created = await api.createTask({
        projectId: form.projectId,
        title: form.title.trim(),
        details: form.details.trim() || null,
        done: false,
        dueDate: form.dueDate || null,
        assigneeId: form.assigneeId || null
      });
      onCreated(created);
    } catch (err) {
      setError(err.detail || 'Could not create task.');
      setBusy(false);
    }
  }

  return (
    <div className="scrim center" onClick={onClose}>
      <div className="sheet" role="dialog" aria-modal="true" aria-label="New task"
           onClick={(e) => e.stopPropagation()}>
        <div className="sheet-head">
          <h2>New task</h2>
          <p>Assign it to a project and optionally set a due date or assignee.</p>
        </div>

        <form onSubmit={submit}>
          <div className="sheet-body">
            {error && <div className="banner warn" role="alert">{error}</div>}

            <div className="field">
              <label htmlFor="nt-project">Project</label>
              <select id="nt-project" value={form.projectId} onChange={set('projectId')} required>
                {projects.map((p) => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="nt-title">Title</label>
              <input id="nt-title" value={form.title} onChange={set('title')}
                     placeholder="What needs to be done?" autoFocus required />
            </div>

            <div className="field">
              <label htmlFor="nt-details">Details</label>
              <textarea id="nt-details" value={form.details} onChange={set('details')}
                        placeholder="Context, acceptance criteria, or notes…" />
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
              <div className="field" style={{ marginBottom: 0 }}>
                <label htmlFor="nt-due">Due date</label>
                <input id="nt-due" type="date" value={form.dueDate} onChange={set('dueDate')} />
              </div>

              <div className="field" style={{ marginBottom: 0 }}>
                <label htmlFor="nt-assignee">Assignee</label>
                <select id="nt-assignee" value={form.assigneeId} onChange={set('assigneeId')}>
                  <option value="">Unassigned</option>
                  {members.map((m) => (
                    <option key={m.id} value={m.id}>{m.username} ({m.email})</option>
                  ))}
                </select>
              </div>
            </div>
          </div>

          <div className="sheet-foot">
            <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn btn-primary"
                    style={{ width: 'auto', marginTop: 0 }} disabled={busy || projects.length === 0}>
              {busy ? 'Creating…' : 'Create task'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
