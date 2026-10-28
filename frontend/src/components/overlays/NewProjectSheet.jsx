import { useState } from 'react';
import * as api from '../../api/index.js';

export default function NewProjectSheet({ onClose, onCreated }) {
  const today = new Date().toISOString().slice(0, 10);
  const nextMonth = new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10);

  const [form, setForm] = useState({
    name: '',
    description: '',
    priority: 'MEDIUM',
    status: 'IN_PROGRESS',
    startDate: today,
    dueDate: nextMonth
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    if (!form.name.trim()) {
      setError('Project name is required.');
      return;
    }
    setError(null);
    setBusy(true);
    try {
      const created = await api.createProject({
        name: form.name.trim(),
        description: form.description.trim() || null,
        priority: form.priority,
        status: form.status,
        startDate: form.startDate || null,
        dueDate: form.dueDate || null
      });
      onCreated(created);
    } catch (err) {
      setError(err.detail || 'Could not create project.');
      setBusy(false);
    }
  }

  return (
    <div className="scrim center" onClick={onClose}>
      <div className="sheet" role="dialog" aria-modal="true" aria-label="New project"
           onClick={(e) => e.stopPropagation()}>
        <div className="sheet-head">
          <h2>New project</h2>
          <p>You become a manager of this project and can invite other members.</p>
        </div>

        <form onSubmit={submit}>
          <div className="sheet-body">
            {error && <div className="banner warn" role="alert">{error}</div>}

            <div className="field">
              <label htmlFor="np-name">Name</label>
              <input id="np-name" value={form.name} onChange={set('name')}
                     placeholder="e.g. Q4 Infrastructure" autoFocus required />
            </div>

            <div className="field">
              <label htmlFor="np-desc">Description</label>
              <textarea id="np-desc" value={form.description} onChange={set('description')}
                        placeholder="Scope and goals of the project…" />
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
              <div className="field">
                <label htmlFor="np-priority">Priority</label>
                <select id="np-priority" value={form.priority} onChange={set('priority')}>
                  <option value="LOW">Low</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="HIGH">High</option>
                  <option value="URGENT">Urgent</option>
                </select>
              </div>

              <div className="field">
                <label htmlFor="np-status">Status</label>
                <select id="np-status" value={form.status} onChange={set('status')}>
                  <option value="PLANNED">Planned</option>
                  <option value="IN_PROGRESS">In progress</option>
                  <option value="BACKLOG">Backlog</option>
                  <option value="COMPLETED">Completed</option>
                  <option value="CANCELLED">Cancelled</option>
                </select>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
              <div className="field" style={{ marginBottom: 0 }}>
                <label htmlFor="np-start">Start date</label>
                <input id="np-start" type="date" value={form.startDate} onChange={set('startDate')} required />
              </div>

              <div className="field" style={{ marginBottom: 0 }}>
                <label htmlFor="np-due">Due date</label>
                <input id="np-due" type="date" value={form.dueDate} onChange={set('dueDate')} required />
              </div>
            </div>
          </div>

          <div className="sheet-foot">
            <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn btn-primary"
                    style={{ width: 'auto', marginTop: 0 }} disabled={busy}>
              {busy ? 'Creating…' : 'Create project'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
