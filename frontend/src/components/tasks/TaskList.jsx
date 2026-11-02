import { useState, useEffect, useMemo } from 'react';
import TaskRow from './TaskRow.jsx';

const PAGE_SIZE = 15;

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
  const [page, setPage] = useState(0);

  const projectsById = useMemo(
    () => Object.fromEntries(projects.map((p) => [p.id, p])),
    [projects]
  );

  const totalPages = Math.max(1, Math.ceil(tasks.length / PAGE_SIZE));

  useEffect(() => {
    if (page >= totalPages) {
      setPage(Math.max(0, totalPages - 1));
    }
  }, [page, totalPages]);

  const pagedTasks = useMemo(() => {
    const start = page * PAGE_SIZE;
    return tasks.slice(start, start + PAGE_SIZE);
  }, [tasks, page]);

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
        <>
          {pagedTasks.map((task, i) => (
            <TaskRow
              key={task.id}
              task={task}
              index={page * PAGE_SIZE + i}
              project={projectsById[task.projectId]}
              selected={!!selected && selected.id === task.id}
              onSelect={onSelect}
              onToggle={onToggle}
            />
          ))}
          {totalPages > 1 && (
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '12px 24px',
              borderTop: '1px solid var(--line)',
              fontSize: '12px',
              color: 'var(--txt-mute)'
            }}>
              <span className="mono">
                Showing {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, tasks.length)} of {tasks.length}
              </span>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <button
                  type="button"
                  className="btn btn-ghost"
                  style={{ padding: '4px 10px', fontSize: '12px' }}
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  ← Prev
                </button>
                <span className="mono">
                  Page {page + 1} of {totalPages}
                </span>
                <button
                  type="button"
                  className="btn btn-ghost"
                  style={{ padding: '4px 10px', fontSize: '12px' }}
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                >
                  Next →
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
