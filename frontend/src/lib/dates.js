import { USE_LIVE_API } from '../config.js';

/**
 * In mock mode, dates are read against a fixed anchor so the fixture data
 * keeps its intended relationships ("today", "tomorrow", overdue). In live
 * mode, we compare against today's UTC midnight.
 */
const MOCK_TODAY = new Date('2026-09-09T00:00:00Z');

function startOfToday() {
  if (!USE_LIVE_API) return MOCK_TODAY;
  const now = new Date();
  return new Date(Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()));
}

function parseDate(iso) {
  if (!iso) return null;
  if (/^\d{4}-\d{2}-\d{2}$/.test(iso)) {
    return new Date(`${iso}T00:00:00Z`);
  }
  return new Date(iso);
}

export function fmtDate(iso) {
  const d = parseDate(iso);
  if (!d || Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
}

export function daysFrom(iso) {
  const d = parseDate(iso);
  if (!d || Number.isNaN(d.getTime())) return null;
  return Math.round((d - startOfToday()) / 86400000);
}

export function dueLabel(iso) {
  const d = daysFrom(iso);
  if (d === null) return '—';
  if (d < 0) return `${Math.abs(d)}d over`;
  if (d === 0) return 'today';
  if (d === 1) return 'tomorrow';
  return fmtDate(iso);
}

export const isOverdue = (task) => !task.done && daysFrom(task.dueDate) !== null && daysFrom(task.dueDate) < 0;
