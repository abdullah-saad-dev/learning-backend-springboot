import { useMemo } from 'react';
import TaskRow from './TaskRow.jsx';

/** An empty screen is an invitation to act, so it always says what would fill it. */
function EmptyState({ title, body }) {
  return (
    <div className="empty">
      <h3>{title}</h3>
      <p>{body}</p>
    </div>
  );
}

export default function TaskList({ tasks, projects, selected, onSelect, onToggle, emptyFor }) {
  const projectsById = useMemo(
    () => Object.fromEntries(projects.map((p) => [p.id, p])),
    [projects]
  );

  return (
    <div className="list">
      {tasks.length === 0 ? (
        <EmptyState title={emptyFor.title} body={emptyFor.body} />
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
