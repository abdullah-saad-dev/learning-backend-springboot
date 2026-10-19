/**
 * Sample data for mock mode. Delete this file once USE_LIVE_API is true and
 * every MOCK function in ./index.js has a real endpoint behind it.
 *
 * Three fields here do not exist on the API's TaskResponse yet — projectId,
 * assigneeId and dueDate. They are the reason the flag cannot be flipped for
 * tasks alone.
 */

export const ME_ID = '01f114c2-5520-77a8-9226-cbfd1ea7e4b2';

export const FIXTURES = {
  me: {
    id: ME_ID,
    username: 'Abdullah Saad',
    email: 'abdullahsaad@gmail.com',
    role: 'ADMIN'
  },

  projects: [
    { id: 'p1', name: 'Task API',          role: 'LEADER', status: 'IN_PROGRESS', priority: 'HIGH',   memberCount: 4, dueDate: '2026-10-07' },
    { id: 'p2', name: 'Auth hardening',    role: 'LEADER', status: 'IN_PROGRESS', priority: 'URGENT', memberCount: 1, dueDate: '2026-09-14' },
    { id: 'p3', name: 'Schema evolution',  role: 'LEADER', status: 'PLANNED',     priority: 'MEDIUM', memberCount: 1, dueDate: '2026-11-01' },
    { id: 'p4', name: 'Capstone frontend', role: 'MEMBER', status: 'IN_PROGRESS', priority: 'HIGH',   memberCount: 5, dueDate: '2026-09-30' },
    { id: 'p5', name: 'React Flow labs',   role: 'MEMBER', status: 'BACKLOG',     priority: 'LOW',    memberCount: 3, dueDate: '2026-12-12' }
  ],

  members: {
    p1: [{ id: ME_ID, username: 'Abdullah Saad' }, { id: 'u2', username: 'Mira' },
         { id: 'u3', username: 'Tomas' }, { id: 'u4', username: 'Wei' }],
    p2: [{ id: ME_ID, username: 'Abdullah Saad' }],
    p3: [{ id: ME_ID, username: 'Abdullah Saad' }],
    p4: [{ id: ME_ID, username: 'Abdullah Saad' }, { id: 'u5', username: 'Priya' },
         { id: 'u6', username: 'Jonas' }, { id: 'u7', username: 'Sam' }, { id: 'u8', username: 'Lena' }],
    p5: [{ id: ME_ID, username: 'Abdullah Saad' }, { id: 'u9', username: 'Dana' },
         { id: 'u10', username: 'Kito' }]
  },

  tasks: [
    { id: 't1', title: 'Drop tasks.owner_id and re-point the service at projects_users',
      details: 'TaskService has 16 references left. The @Query is a string, so it fails at context load, not compile.',
      projectId: 'p1', assigneeId: ME_ID, done: false, version: 3, dueDate: '2026-09-10',
      createdAt: '2026-09-06T09:12:00Z' },
    { id: 't2', title: 'Give ProjectStatus and ProjectPriority real constants',
      details: "V6's CHECK constraints already fix the values. @Enumerated(STRING) writes name() verbatim, so they must match exactly.",
      projectId: 'p1', assigneeId: ME_ID, done: false, version: 0, dueDate: '2026-09-09',
      createdAt: '2026-09-07T11:40:00Z' },
    { id: 't3', title: 'Settle the projects_users vs project_users name split',
      details: 'V6 creates projects_users; V7 and R__seed still insert into project_users. The chain fails on a fresh database.',
      projectId: 'p1', assigneeId: 'u2', done: false, version: 1, dueDate: '2026-09-08',
      createdAt: '2026-09-07T14:02:00Z' },
    { id: 't4', title: 'POST /auth/logout and /auth/logout-all',
      details: 'revokeFamily already exists and is exactly the operation. Nothing calls it outside reuse detection.',
      projectId: 'p2', assigneeId: ME_ID, done: false, version: 0, dueDate: '2026-09-12',
      createdAt: '2026-09-05T08:20:00Z' },
    { id: 't5', title: 'RFC 7807 for filter-level 401s',
      details: 'Bearer-token failures never reach ApiExceptionHandler. The fix is exceptionHandling() on the chain.',
      projectId: 'p2', assigneeId: ME_ID, done: false, version: 2, dueDate: '2026-09-16',
      createdAt: '2026-09-04T16:45:00Z' },
    { id: 't6', title: 'Pass APP_JWT_SECRET through docker-compose',
      details: 'JwtConfig fails startup without it and it is missing from example.env too.',
      projectId: 'p2', assigneeId: ME_ID, done: true, version: 1, dueDate: '2026-09-03',
      createdAt: '2026-09-01T10:05:00Z' },
    { id: 't7', title: 'Write the expand/migrate/contract deck',
      details: 'V4 and V7 are two worked examples with a runnable database behind them.',
      projectId: 'p3', assigneeId: ME_ID, done: false, version: 0, dueDate: '2026-10-20',
      createdAt: '2026-09-07T19:30:00Z' },
    { id: 't8', title: 'Move R__seed behind a dev-only flyway location',
      details: 'It runs in every environment and creates a real ADMIN login with a committed bcrypt hash.',
      projectId: 'p3', assigneeId: ME_ID, done: false, version: 0, dueDate: '2026-09-25',
      createdAt: '2026-09-06T13:15:00Z' },
    { id: 't9', title: 'Wire the task list to GET /api/tasks',
      details: '',
      projectId: 'p4', assigneeId: ME_ID, done: false, version: 0, dueDate: '2026-09-11',
      createdAt: '2026-09-08T09:00:00Z' },
    { id: 't10', title: "Review Priya's kanban drag interaction",
      details: '',
      projectId: 'p4', assigneeId: 'u5', done: false, version: 4, dueDate: '2026-09-13',
      createdAt: '2026-09-07T15:22:00Z' },
    { id: 't11', title: 'Set up a CI workflow that only needs a Docker daemon',
      details: 'Several Spring contexts start more than one PostgreSQL container per run.',
      projectId: 'p4', assigneeId: ME_ID, done: true, version: 2, dueDate: '2026-09-02',
      createdAt: '2026-08-30T12:00:00Z' },
    { id: 't12', title: 'Read the React Flow custom-node docs',
      details: '',
      projectId: 'p5', assigneeId: 'u9', done: false, version: 0, dueDate: '2026-11-30',
      createdAt: '2026-09-05T20:10:00Z' }
  ],

  sessions: [
    { familyId: '01f11a3c-8f21-7c04-9a11-4b7d2e6f0011', device: 'Chrome on Windows',  ip: '197.45.12.8',   lastSeenAt: '2026-09-09T08:41:00Z', current: true },
    { familyId: '01f0f8d1-2c55-7bb9-8e30-9d1c7a4e5522', device: 'Firefox on Windows', ip: '197.45.12.8',   lastSeenAt: '2026-09-07T21:03:00Z', current: false },
    { familyId: '01f0e2a7-77b3-7d18-83c2-1e5f9b0c8833', device: 'Safari on iPhone',   ip: '41.238.90.140', lastSeenAt: '2026-09-02T17:55:00Z', current: false }
  ]
};
