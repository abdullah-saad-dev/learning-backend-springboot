import { useState, useEffect } from 'react';
import * as api from '../../api/index.js';
import { LockIcon } from '../Icon.jsx';
import { fmtDate, dueLabel, daysFrom } from '../../lib/dates.js';
import { isLead, roleClass, roleLabel, isSolo } from '../../lib/roles.js';

/**
 * A 409 is not a generic failure — it means someone else moved the row. The
 * strip says so and offers the only useful next step, rather than a toast
 * that quietly discards the edit.
 */
function ConflictStrip({ version, onResolve }) {
  return (
    <div style={{ padding: '14px 18px 0' }}>
      <div className="conflict" role="alert">
        <b>Someone else changed this task</b>
        <p>
          Your copy is version {version}. The server has moved past it, so this
          edit was refused rather than overwriting theirs.
        </p>
        <div className="row">
          <button className="btn btn-ghost" onClick={onResolve}>Load their version</button>
        </div>
      </div>
    </div>
  );
}

function formatAction(action) {
  return (action || '').replace(/_/g, ' ').toLowerCase();
}

export default function Inspector({
  task,
  project,
  me,
  conflict,
  onResolve,
  onClose,
  onUpdated,
  onDeleted,
  onConflict
}) {
  const canEdit = isLead(project?.role) || task.assigneeId === me?.id || me?.role === 'ADMIN';
  const canDelete = isLead(project?.role) || me?.role === 'ADMIN';

  const [title, setTitle] = useState(task.title || '');
  const [details, setDetails] = useState(task.details || '');
  const [dueDate, setDueDate] = useState(task.dueDate || '');
  const [assigneeId, setAssigneeId] = useState(task.assigneeId || '');
  const [members, setMembers] = useState([]);
  const [activity, setActivity] = useState([]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    setTitle(task.title || '');
    setDetails(task.details || '');
    setDueDate(task.dueDate || '');
    setAssigneeId(task.assigneeId || '');
    setError(null);
  }, [task.id, task.version, task.title, task.details, task.dueDate, task.assigneeId]);

  useEffect(() => {
    if (!project?.id) return;
    let live = true;
    Promise.all([
      api.listMembers(project.id).catch(() => []),
      api.listActivity(project.id).catch(() => [])
    ]).then(([m, a]) => {
      if (!live) return;
      setMembers(m || []);
      setActivity(a || []);
    });
    return () => { live = false; };
  }, [project?.id, task.version]);

  const dirty =
    title !== (task.title || '') ||
    details !== (task.details || '') ||
    (dueDate || '') !== (task.dueDate || '') ||
    (assigneeId || '') !== (task.assigneeId || '');

  async function saveChanges(e) {
    if (e) e.preventDefault();
    if (!title.trim()) {
      setError('Title cannot be blank.');
      return;
    }
    setError(null);
    setSaving(true);
    try {
      const updated = await api.updateTask(task.id, {
        title: title.trim(),
        details: details.trim() || null,
        done: task.done,
        dueDate: dueDate || null,
        assigneeId: assigneeId || null,
        version: task.version
      });
      onUpdated(updated);
    } catch (err) {
      if (err.conflict) {
        onConflict(task.id);
      } else {
        setError(err.detail || 'Could not save task.');
      }
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    setError(null);
    setSaving(true);
    try {
      await api.deleteTask(task.id, task.version);
      onDeleted(task.id);
    } catch (err) {
      if (err.conflict) {
        onConflict(task.id);
      } else {
        setError(err.detail || 'Could not delete task.');
      }
      setSaving(false);
    }
  }

  const overdue = !task.done && daysFrom(task.dueDate) !== null && daysFrom(task.dueDate) < 0;
  const currentAssignee = members.find((m) => m.id === task.assigneeId);
  const taskActivity = activity.filter((a) => a.entityId === task.id).slice(0, 8);
  const shownActivity = taskActivity.length > 0 ? taskActivity : activity.slice(0, 6);

  return (
    <aside className="panel" aria-label="Task detail">
      <div className="panel-head">
        <h2>{task.title}</h2>
        <button className="btn btn-ghost" style={{ padding: '4px 9px', flex: 'none' }}
                onClick={onClose} aria-label="Close detail">✕</button>
      </div>

      {conflict && <ConflictStrip version={task.version} onResolve={onResolve} />}

      {error && (
        <div style={{ padding: '12px 18px 0' }}>
          <div className="banner warn" style={{ marginBottom: 0 }} role="alert">{error}</div>
        </div>
      )}

      {canEdit ? (
        <form className="panel-sec" onSubmit={saveChanges}>
          <span className="mono">Edit task</span>
          <div className="field" style={{ marginBottom: '10px' }}>
            <label htmlFor="insp-title">Title</label>
            <input id="insp-title" value={title} onChange={(e) => setTitle(e.target.value)} required />
          </div>

          <div className="field" style={{ marginBottom: '10px' }}>
            <label htmlFor="insp-details">Details</label>
            <textarea id="insp-details" value={details} onChange={(e) => setDetails(e.target.value)}
                      placeholder="Add notes or details…" />
          </div>

          <div className="field" style={{ marginBottom: '10px' }}>
            <label htmlFor="insp-due">Due date</label>
            <input id="insp-due" type="date" value={dueDate || ''}
                   onChange={(e) => setDueDate(e.target.value)} />
          </div>

          <div style={{ display: 'flex', gap: '8px', justifyContent: 'space-between', marginTop: '12px' }}>
            {canDelete ? (
              <button type="button" className="btn btn-danger"
                      style={{ padding: '7px 11px', fontSize: '12.5px' }}
                      onClick={handleDelete} disabled={saving}>
                Delete
              </button>
            ) : <span />}

            <button type="submit" className="btn btn-primary"
                    style={{ width: 'auto', marginTop: 0, padding: '7px 13px', fontSize: '12.5px' }}
                    disabled={!dirty || saving}>
              {saving ? 'Saving…' : 'Save changes'}
            </button>
          </div>
        </form>
      ) : (
        task.details && (
          <div className="panel-sec">
            <span className="mono">Details</span>
            <p style={{ margin: 0, fontSize: '13px', lineHeight: 1.65, color: 'var(--txt-dim)' }}>
              {task.details}
            </p>
          </div>
        )
      )}

      <div className="panel-sec">
        <span className="mono">Assignee</span>
        {isSolo(project) ? (
          <>
            <div className="locked">
              <LockIcon /> {currentAssignee ? currentAssignee.username : 'You'}
            </div>
            <p className="note">
              You are the only member of {project.name}. Add a second member to assign tasks to others.
            </p>
          </>
        ) : canEdit ? (
          <div className="field" style={{ marginBottom: 0 }}>
            <select
              aria-label="Assignee"
              value={assigneeId || ''}
              onChange={(e) => setAssigneeId(e.target.value)}
            >
              <option value="">Unassigned</option>
              {members.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.username} ({m.email})
                </option>
              ))}
            </select>
            {assigneeId !== (task.assigneeId || '') && (
              <p className="note">Click “Save changes” above to apply the new assignee.</p>
            )}
          </div>
        ) : (
          <div className="locked">
            {currentAssignee ? `${currentAssignee.username} (${currentAssignee.email})` : 'Unassigned'}
          </div>
        )}
      </div>

      <div className="panel-sec">
        <span className="mono">Properties</span>
        <dl className="kv">
          <dt>Project</dt>
          <dd>
            {project ? (
              <>
                <span className={`role-chip ${roleClass(project)}`}>
                  <i />{roleLabel(project)}
                </span>
                <span style={{ marginLeft: '8px' }}>{project.name}</span>
              </>
            ) : '—'}
          </dd>

          <dt>Status</dt>
          <dd>{task.done ? 'Done' : 'Open'}</dd>

          <dt>Due</dt>
          <dd className={overdue ? 'tdate over' : ''}>{dueLabel(task.dueDate)}</dd>

          <dt>Created</dt>
          <dd className="mono" style={{ textTransform: 'none' }}>{fmtDate(task.createdAt)}</dd>

          <dt>Version</dt>
          <dd className="mono" style={{ textTransform: 'none' }}>{task.version}</dd>
        </dl>
      </div>

      <div className="panel-sec">
        <span className="mono">Activity</span>
        {shownActivity.length === 0 ? (
          <p className="note" style={{ marginTop: 0 }}>No activity recorded yet.</p>
        ) : (
          <div style={{ display: 'grid', gap: '8px' }}>
            {shownActivity.map((item) => {
              const actor = members.find((m) => m.id === item.actorId);
              return (
                <div key={item.id} style={{
                  padding: '8px 10px',
                  background: 'var(--void)',
                  border: '1px solid var(--line)',
                  borderRadius: 'var(--r-sm)',
                  fontSize: '12px'
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: '8px' }}>
                    <b style={{ color: 'var(--txt)', fontWeight: 500, textTransform: 'capitalize' }}>
                      {formatAction(item.action)}
                    </b>
                    <span className="mono" style={{ fontSize: '10px', textTransform: 'none' }}>
                      {fmtDate(item.createdAt)}
                    </span>
                  </div>
                  <div style={{ color: 'var(--txt-mute)', marginTop: '3px' }}>
                    {actor ? actor.username : 'Member'}
                    {item.changes?.title ? ` · “${item.changes.title}”` : ''}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </aside>
  );
}
