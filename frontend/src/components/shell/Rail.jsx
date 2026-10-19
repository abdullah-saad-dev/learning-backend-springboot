import { PRODUCT } from '../../config.js';
import { InboxIcon } from '../Icon.jsx';
import ProjectGroup from './ProjectGroup.jsx';
import AccountMenu from './AccountMenu.jsx';

/**
 * The left rail: where you are, and what you are.
 *
 * Projects are grouped by role rather than sorted into one list, because the
 * grouping is the answer to the question the colour code raises.
 */
export default function Rail({ me, projects, tasks, view, onView, onSessions, onLogout }) {
  const led = projects.filter((p) => p.role === 'LEADER');
  const joined = projects.filter((p) => p.role !== 'LEADER');

  const openMine = tasks.filter((t) => t.assigneeId === me.id && !t.done).length;
  const openAll = tasks.filter((t) => !t.done).length;
  const openCountFor = (p) => tasks.filter((t) => t.projectId === p.id && !t.done).length;

  return (
    <nav className="rail" aria-label="Projects">
      {/* One continuous rule down the rail. Every project notch sits on it. */}
      <div className="spine rail-spine" aria-hidden="true" />

      <div className="rail-head">
        <div className="wordmark"><i />{PRODUCT}</div>
        <span className="kbd">⌘K</span>
      </div>

      <div className="rail-scroll">
        <button className="nav-item"
                aria-current={view.type === 'mine' ? 'true' : 'false'}
                onClick={() => onView({ type: 'mine' })}>
          <InboxIcon /> My tasks <span className="count">{openMine}</span>
        </button>

        <button className="nav-item"
                aria-current={view.type === 'all' ? 'true' : 'false'}
                onClick={() => onView({ type: 'all' })}>
          <InboxIcon /> Everything <span className="count">{openAll}</span>
        </button>

        <ProjectGroup label="You lead" projects={led} view={view}
                      openCountFor={openCountFor} onView={onView} />
        <ProjectGroup label="You are in" projects={joined} view={view}
                      openCountFor={openCountFor} onView={onView} />
      </div>

      <AccountMenu me={me} onSessions={onSessions} onLogout={onLogout} />
    </nav>
  );
}
