import { useMemo } from 'react';
import TaskRow from './TaskRow.jsx';

/** An empty screen is an invitation to act, so it always says what would fill it. */
function EmptyState({ title, body, hasProjects, onNewTask, onNewProject }) {
  return (
    <div className="empty">
      <h3>{title}</h3>
      <p>{body}</p>
      <div style={{ display: 'flex', justifyContent: 'center', gap: '10px', flexWrap: 'wrap' }}>
        {hasProjects && onNewTask && (
          <button type="button" className="btn btn-cta" onClick={onNewTask}>
            + Add a task
          </button>
        )}
        {onNewProject && (
          <button
            type="button"
            className={hasProjects ? 'btn btn-cta-outline' : 'btn btn-cta'}
            onClick={onNewProject}
          >
            + Create a project
          </button>
        )}
      </div>
    </div>
  );
}

export default function TaskList({
  tasks,
  projects,
  selected,
  onSelect,
  onToggle,
  onNewTask,
  onNewProject,
  emptyFor
}) {
  const projectsById = useMemo(
    () => Object.fromEntries(projects.map((p) => [p.id, p])),
    [projects]
  );

  return (
    <div className="list">
      {tasks.length === 0 ? (
        <EmptyState
          title={emptyFor.title}
          body={emptyFor.body}
          hasProjects={projects.length > 0}
          onNewTask={onNewTask}
          onNewProject={onNewProject}
        />
      ) : (
        tasks.map((task, i) => (
          <TaskRow
            key={task.id}
            task={task}
            index={i}
            project={projectsById[task.projectId]}
            selected={!!selected && selected.id === task.id}
            onSelect={onSelect}
            onToggle={onToggle}
          />
        ))
      )}
    </div>
  );
}
