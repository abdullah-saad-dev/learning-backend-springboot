import { roleClass, roleLabel, isSolo } from '../../lib/roles.js';

const FILTERS = [
  ['open', 'Open'],
  ['done', 'Done'],
  ['all', 'All']
];

export default function TopBar({
  heading,
  project,
  query,
  onQuery,
  filter,
  onFilter,
  onNewTask,
  onOpenMembers
}) {
  return (
    <header className="topbar">
      <h1>
        {/* The name must never be squeezed out by the chips beside it. */}
        <span className="name">{heading}</span>

        {project && (
          <span className={`role-chip ${roleClass(project)}`}>
            <i />{roleLabel(project)}
          </span>
        )}

        {project && isSolo(project) && (
          <span className="role-chip"
                title="You are the only member, so tasks here are assigned to you">
            Solo project
          </span>
        )}

        {project && (
          <button
            type="button"
            className="role-chip"
            style={{ cursor: 'pointer' }}
            onClick={onOpenMembers}
            title="View and manage project members"
          >
            {project.memberCount} {project.memberCount === 1 ? 'member' : 'members'}
          </button>
        )}
      </h1>

      <input className="search" value={query} placeholder="Search titles…"
             onChange={(e) => onQuery(e.target.value)} aria-label="Search tasks" />

      <div className="segmented" role="group" aria-label="Filter">
        {FILTERS.map(([key, label]) => (
          <button key={key} aria-pressed={filter === key ? 'true' : 'false'}
                  onClick={() => onFilter(key)}>
            {label}
          </button>
        ))}
      </div>

      {onNewTask && (
        <button
          type="button"
          className="btn btn-ghost"
          style={{ padding: '6px 11px', fontSize: '12.5px', flex: 'none' }}
          onClick={onNewTask}
        >
          + New task
        </button>
      )}
    </header>
  );
}
