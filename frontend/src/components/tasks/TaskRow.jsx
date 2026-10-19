import { CheckIcon } from '../Icon.jsx';
import { dueLabel, isOverdue } from '../../lib/dates.js';
import { roleClass } from '../../lib/roles.js';

export default function TaskRow({ task, project, index, selected, onSelect, onToggle }) {
  const over = isOverdue(task);

  return (
    <div
      className="trow"
      data-done={String(task.done)}
      aria-current={selected ? 'true' : 'false'}
      onClick={() => onSelect(task)}
      style={{ animationDelay: `${index * 14}ms` }}
    >
      {/* stopPropagation so completing a task does not also select it */}
      <button
        className="check"
        data-done={String(task.done)}
        aria-label={`${task.done ? 'Reopen' : 'Complete'} ${task.title}`}
        onClick={(e) => { e.stopPropagation(); onToggle(task); }}
      >
        {task.done && <CheckIcon />}
      </button>

      <button className="ttitle" style={{ textAlign: 'left' }} onClick={() => onSelect(task)}>
        {task.title}
      </button>

      <span className="tproject">
        {project && <i className={roleClass(project)} />}
        {project ? project.name : 'No project'}
      </span>

      <span className={`tdate${over ? ' over' : ''}`}>{dueLabel(task.dueDate)}</span>
    </div>
  );
}
