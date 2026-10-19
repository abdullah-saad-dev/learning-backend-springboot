import { roleClass, roleLabel, isSolo } from '../../lib/roles.js';

const FILTERS = [
  ['open', 'Open'],
  ['done', 'Done'],
  ['all', 'All']
];

export default function TopBar({ heading, project, query, onQuery, filter, onFilter }) {
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
    </header>
  );
}
