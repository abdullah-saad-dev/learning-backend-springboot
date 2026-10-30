# Keel — frontend

React + Vite. Currently runs on mock data; the backend is not wired up yet.

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

## Going live

`src/config.js` holds one flag:

```js
export const USE_LIVE_API = false;
```

Flip it and every call in `src/api/index.js` marked **REAL** starts hitting Spring
Boot through the Vite proxy — no CORS configuration, and the `SameSite=Strict`
refresh cookie is sent because the browser stays on one origin.

Calls marked **MOCK** need endpoints that do not exist yet. Each one names the
route it is waiting for and the JSON it expects back:

| Missing endpoint | Feeds |
|---|---|
| `GET /auth/me` | the signed-in name and email in the rail |
| `GET /auth/sessions` | the active-sessions sheet |
| `POST /auth/logout`, `POST /auth/logout-all` | both log-out actions |
| `GET /api/projects` | the whole project menu and the gold/blue split |
| `GET /api/projects/{id}/members` | the solo auto-assign rule |
| `GET /api/projects/{id}/activity` | the Activity panel (`audit_logs`) |

**`GET /api/tasks` is the blocker even though it exists.** `TaskResponse` carries
no `projectId`, no assignee and no due date, and the UI needs all three. Until
they are on the response, tasks cannot come from the live API either.

## Layout

```
src/
  config.js              product name + the live/mock flag
  App.jsx                state, routing between views, the one place effects live
  api/
    client.js            fetch, auth header, 409 → conflict
    index.js             every call, each marked REAL or MOCK
    fixtures.js          sample data — delete when USE_LIVE_API is true
  lib/
    dates.js             due labels, overdue
    roles.js             LEADER → gold, MEMBER → blue, solo
  components/
    Icon.jsx  Stub.jsx
    auth/     AuthScreen
    shell/    Rail, ProjectGroup, AccountMenu, TopBar
    tasks/    TaskList, TaskRow, Inspector
    overlays/ Palette, SessionsSheet, ConfirmSheet
  styles/                base, spine, auth, shell, overlays, responsive
```

CSS is split by area rather than per component, and all breakpoints live
together in `responsive.css` so they can be read against each other.

## The design

Role is the organising idea. Gold means you lead a project, blue means you are
a member, and neither colour is ever used for decoration. Both hang off *the
spine* — one hairline that appears at the same offset on every screen: on the
auth page the legend sits on it, in the app it runs down the rail and every
project puts a notch on it.

Type: Familjen Grotesk for display, IBM Plex Sans for the interface, IBM Plex
Mono for data (dates, versions, statuses).
