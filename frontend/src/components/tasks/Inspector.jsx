import { LockIcon } from '../Icon.jsx';
import Stub from '../Stub.jsx';
import { fmtDate, dueLabel, daysFrom } from '../../lib/dates.js';
import { roleClass, roleLabel, isSolo } from '../../lib/roles.js';

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

function Assignee({ project }) {
  if (isSolo(project)) {
    return (
      <>
        <div className="locked"><LockIcon /> You</div>
        <p className="note">
          You are the only member of {project.name}, so every task in it is yours
          automatically. Add a second member to choose an assignee.
        </p>
      </>
    );
  }
  return (
    <Stub>
      Choosing an assignee needs a person on the task. There is no{' '}
      <code>assignee_id</code> column and no join between tasks and{' '}
      <code>projects_users</code> yet.
    </Stub>
  );
}

export default function Inspector({ task, project, conflict, onResolve, onClose }) {
  const overdue = !task.done && daysFrom(task.dueDate) < 0;

  return (
    <aside className="panel" aria-label="Task detail">
      <div className="panel-head">
        <h2>{task.title}</h2>
        <button className="btn btn-ghost" style={{ padding: '4px 9px', flex: 'none' }}
                onClick={onClose} aria-label="Close detail">✕</button>
      </div>

      {conflict && <ConflictStrip version={task.version} onResolve={onResolve} />}

      {task.details && (
        <div className="panel-sec">
          <span className="mono">Details</span>
          <p style={{ margin: 0, fontSize: '13px', lineHeight: 1.65, color: 'var(--txt-dim)' }}>
            {task.details}
          </p>
        </div>
      )}

      <div className="panel-sec">
        <span className="mono">Assignee</span>
        <Assignee project={project} />
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
        <Stub>
          The <code>audit_logs</code> table exists with the right columns and nothing
          writes to it. When it does, every change lands here.
        </Stub>
      </div>
    </aside>
  );
}
