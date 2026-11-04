import { roleClass, roleLabel, isSolo } from '../../lib/roles.js';

const TASK_FILTERS = [
  ['open', 'Open'],
  ['done', 'Done'],
  ['all', 'All']
];

const PROJECT_FILTERS = [
  ['all', 'All'],
  ['lead', 'Leading'],
  ['member', 'Member']
];

export default function TopBar({
  heading,
  isProjectsView,
  project,
  query,
  onQuery,
  filter,
  onFilter,
  projectRoleFilter,
  onProjectRoleFilter,
  onNewTask,
  onNewProject,
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

      <input
        className="search"
        value={query}
        placeholder={isProjectsView ? 'Search projects…' : 'Search tasks…'}
        onChange={(e) => onQuery(e.target.value)}
        aria-label={isProjectsView ? 'Search projects' : 'Search tasks'}
      />

      {isProjectsView ? (
        <div className="segmented" role="group" aria-label="Filter projects by role">
          {PROJECT_FILTERS.map(([key, label]) => (
            <button
              key={key}
              aria-pressed={projectRoleFilter === key ? 'true' : 'false'}
              onClick={() => onProjectRoleFilter(key)}
            >
              {label}
            </button>
          ))}
        </div>
      ) : (
        <div className="segmented" role="group" aria-label="Filter tasks">
          {TASK_FILTERS.map(([key, label]) => (
            <button
              key={key}
              aria-pressed={filter === key ? 'true' : 'false'}
              onClick={() => onFilter(key)}
            >
              {label}
            </button>
          ))}
        </div>
      )}

      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flex: 'none' }}>
        {isProjectsView ? (
          onNewProject && (
            <button
              type="button"
              className="btn btn-cta"
              style={{ padding: '7px 13px', fontSize: '12.5px' }}
              onClick={onNewProject}
            >
              + New project
            </button>
          )
        ) : (
          onNewTask && (
            <button
              type="button"
              className="btn btn-cta"
              style={{ padding: '7px 13px', fontSize: '12.5px' }}
              onClick={onNewTask}
            >
              + New task
            </button>
          )
        )}
      </div>
    </header>
  );
}
