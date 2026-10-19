/**
 * Dates are read against TODAY rather than the system clock so the fixture
 * data keeps its intended relationships — "today", "tomorrow", overdue —
 * whenever the prototype is opened. Delete this constant and use new Date()
 * once the data is real.
 */
const TODAY = new Date('2026-09-09T00:00:00Z');

export function fmtDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
}

export function daysFrom(iso) {
  if (!iso) return null;
  return Math.round((new Date(iso) - TODAY) / 86400000);
}

export function dueLabel(iso) {
  const d = daysFrom(iso);
  if (d === null) return '—';
  if (d < 0) return `${Math.abs(d)}d over`;
  if (d === 0) return 'today';
  if (d === 1) return 'tomorrow';
  return fmtDate(iso);
}

export const isOverdue = (task) => !task.done && daysFrom(task.dueDate) < 0;
