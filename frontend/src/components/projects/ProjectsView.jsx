import { roleClass, roleLabel, isSolo } from '../../lib/roles.js';
import { fmtDate } from '../../lib/dates.js';

function statusLabel(status) {
  if (!status) return 'In progress';
  return status.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
}

function ProjectCard({ project, tasks, onOpenProject, onOpenMembers, onNewTaskForProject }) {
  const projectTasks = tasks.filter((t) => t.projectId === project.id);
  const openCount = projectTasks.filter((t) => !t.done).length;
  const doneCount = projectTasks.filter((t) => t.done).length;
  const totalCount = projectTasks.length;
  const pct = totalCount > 0 ? Math.round((doneCount / totalCount) * 100) : 0;
  const rClass = roleClass(project);

  return (
    <div className={`pcard ${rClass}`}>
      <div className="pcard-top">
        <div style={{ minWidth: 0 }}>
          <div className="pcard-title-row">
            <button
              type="button"
              className="pcard-title"
              onClick={() => onOpenProject(project.id)}
            >
              {project.name}
            </button>
            <span className={`role-chip ${rClass}`}>
              <i />{roleLabel(project)}
            </span>
            {isSolo(project) && <span className="solo-tag">solo</span>}
          </div>
          {project.description && (
            <p className="pcard-desc">{project.description}</p>
          )}
        </div>

        <div className="pcard-actions">
          <button
            type="button"
            className="btn btn-ghost"
            style={{ padding: '5px 10px', fontSize: '12px' }}
            onClick={() => onOpenMembers(project)}
          >
            {project.memberCount || 1} {(project.memberCount || 1) === 1 ? 'member' : 'members'}
          </button>
          <button
            type="button"
            className="btn btn-ghost"
            style={{ padding: '5px 10px', fontSize: '12px' }}
            onClick={() => onNewTaskForProject(project.id)}
          >
            + Task
          </button>
          <button
            type="button"
            className="btn btn-cta-outline"
            style={{ padding: '5px 11px', fontSize: '12px' }}
            onClick={() => onOpenProject(project.id)}
          >
            View tasks →
          </button>
        </div>
      </div>

      <div className="pcard-foot">
        <div className="pcard-meta">
          <span className="mono">{statusLabel(project.status)}</span>
          {project.priority && (
            <>
              <span>·</span>
              <span className="mono">{project.priority}</span>
            </>
          )}
          {project.dueDate && (
            <>
              <span>·</span>
              <span className="mono">Due {fmtDate(project.dueDate)}</span>
            </>
          )}
        </div>

        <div className="pcard-progress">
          <span className="mono">
            {openCount} open · {doneCount} done
          </span>
          <div className="pcard-bar" title={`${pct}% completed`}>
            <span style={{ width: `${pct}%` }} />
          </div>
        </div>
      </div>
    </div>
  );
}

export default function ProjectsView({
  projects,
  tasks,
  query,
  roleFilter,
  onOpenProject,
  onOpenMembers,
  onNewProject,
  onNewTaskForProject
}) {
  const needle = (query || '').trim().toLowerCase();
  const filtered = projects.filter((p) => {
    const isLead = p.role === 'LEADER' || p.role === 'MANAGER';
    if (roleFilter === 'lead' && !isLead) return false;
    if (roleFilter === 'member' && isLead) return false;
    if (needle) {
      const inName = p.name?.toLowerCase().includes(needle);
      const inDesc = p.description?.toLowerCase().includes(needle);
      if (!inName && !inDesc) return false;
    }
    return true;
  });

  const led = filtered.filter((p) => p.role === 'LEADER' || p.role === 'MANAGER');
  const joined = filtered.filter((p) => p.role !== 'LEADER' && p.role !== 'MANAGER');

  if (projects.length === 0) {
    return (
      <div className="list">
        <div className="empty">
          <h3>No projects yet</h3>
          <p>Create your first project to organize tasks and collaborate with your team.</p>
          <button type="button" className="btn btn-cta" onClick={onNewProject}>
            + New project
          </button>
        </div>
      </div>
    );
  }

  if (filtered.length === 0) {
    return (
      <div className="list">
        <div className="empty">
          <h3>No matching projects</h3>
          <p>Try a different search or switch the role filter above.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="list projects-board">
      {led.length > 0 && (
        <section className="psection">
          <div className="psection-head">
            <span className="mono">Projects you lead ({led.length})</span>
          </div>
          <div className="pgrid">
            {led.map((p) => (
              <ProjectCard
                key={p.id}
                project={p}
                tasks={tasks}
                onOpenProject={onOpenProject}
                onOpenMembers={onOpenMembers}
                onNewTaskForProject={onNewTaskForProject}
              />
            ))}
          </div>
        </section>
      )}

      {joined.length > 0 && (
        <section className="psection">
          <div className="psection-head">
            <span className="mono">Projects you are in ({joined.length})</span>
          </div>
          <div className="pgrid">
            {joined.map((p) => (
              <ProjectCard
                key={p.id}
                project={p}
                tasks={tasks}
                onOpenProject={onOpenProject}
                onOpenMembers={onOpenMembers}
                onNewTaskForProject={onNewTaskForProject}
              />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
