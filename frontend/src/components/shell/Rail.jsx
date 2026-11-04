import { PRODUCT } from '../../config.js';
import { InboxIcon } from '../Icon.jsx';
import ProjectGroup from './ProjectGroup.jsx';
import AccountMenu from './AccountMenu.jsx';

/**
 * The left rail: organized into two clear sections — "My Tasks" and "My Projects".
 */
export default function Rail({
  me,
  projects,
  tasks,
  view,
  onView,
  onNewTask,
  onNewProject,
  onOpenProfile,
  onLogout
}) {
  const led = projects.filter((p) => p.role === 'LEADER' || p.role === 'MANAGER');
  const joined = projects.filter((p) => p.role !== 'LEADER' && p.role !== 'MANAGER');

  const openMine = tasks.filter((t) => t.assigneeId === me.id && !t.done).length;
  const openAll = tasks.filter((t) => !t.done).length;
  const openCountFor = (p) => tasks.filter((t) => t.projectId === p.id && !t.done).length;

  return (
    <nav className="rail" aria-label="Workspace navigation">
      {/* One continuous rule down the rail. Every project notch sits on it. */}
      <div className="spine rail-spine" aria-hidden="true" />

      <div className="rail-head">
        <div className="wordmark"><i />{PRODUCT}</div>
        <span className="kbd" title="Command palette (Ctrl+K)">⌘K</span>
      </div>

      <div className="rail-scroll">
        {/* ── Section 1: My Tasks ── */}
        <div className="rail-section">
          <div className="rail-section-head">
            <span className="mono">My Tasks</span>
            <button
              type="button"
              className="group-add-btn"
              onClick={onNewTask}
              title="Create a new task"
            >
              + Task
            </button>
          </div>

          <button
            className="nav-item"
            aria-current={view.type === 'mine' ? 'true' : 'false'}
            onClick={() => onView({ type: 'mine' })}
          >
            <InboxIcon /> Assigned to me <span className="count">{openMine}</span>
          </button>

          <button
            className="nav-item"
            aria-current={view.type === 'all' ? 'true' : 'false'}
            onClick={() => onView({ type: 'all' })}
          >
            <InboxIcon /> All workspace tasks <span className="count">{openAll}</span>
          </button>
        </div>

        {/* ── Section 2: My Projects ── */}
        <div className="rail-section">
          <div className="rail-section-head">
            <span className="mono">My Projects</span>
            <button
              type="button"
              className="group-add-btn"
              onClick={onNewProject}
              title="Create a new project"
            >
              + Project
            </button>
          </div>

          <button
            className="nav-item"
            aria-current={view.type === 'projects' ? 'true' : 'false'}
            onClick={() => onView({ type: 'projects' })}
          >
            <InboxIcon /> Projects overview <span className="count">{projects.length}</span>
          </button>

          <ProjectGroup
            label="You lead"
            projects={led}
            view={view}
            openCountFor={openCountFor}
            onView={onView}
          />
          <ProjectGroup
            label="You are in"
            projects={joined}
            view={view}
            openCountFor={openCountFor}
            onView={onView}
          />
        </div>
      </div>

      <AccountMenu me={me} onOpenProfile={onOpenProfile} onLogout={onLogout} />
    </nav>
  );
}
