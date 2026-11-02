# Managable — Frontend

React 18 + Vite single-page application for the multi-tenant Projects & Tasks platform. Connected to the live Spring Boot API (`USE_LIVE_API = true`).

## Running locally (development)

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

In development, [`vite.config.js`](vite.config.js) proxies `/api` and `/auth` to `http://localhost:8080`. Because the browser only talks to `localhost:5173`, requests stay same-origin and the `SameSite=Strict` `HttpOnly` refresh cookie is sent automatically on `/auth/refresh`.

## Running in Docker (production build)

From the repository root:

```bash
docker compose up --build
```

[`frontend/Dockerfile`](Dockerfile) performs a two-stage build:
1. **`node:22-alpine` (`build`)** — runs `npm ci && npm run build` to produce static assets in `/app/dist`.
2. **`nginx:1.27-alpine` (`runtime`)** — serves `/usr/share/nginx/html` with SPA history fallback and reverse-proxies `/api/` and `/auth/` to `http://app:8080` via [`nginx.conf`](nginx.conf).

## Key Features

- **Silent Session Restoration & Auto-Refresh:** On boot, [`App.jsx`](src/App.jsx) calls `POST /auth/refresh` using the `HttpOnly` cookie to restore the session and populate `GET /auth/me`. Any `401` on an authenticated call transparently refreshes the access token once before retrying ([`src/api/client.js`](src/api/client.js)).
- **Role-Driven Workspace:** Gold denotes projects you lead (`MANAGER`); blue denotes projects where you are a `MEMBER`. Solo projects (1 member) automatically lock task assignment to the creator.
- **Optimistic Locking & Conflict Resolution:** Task toggles, task edits, and project deletions carry the entity's `version`. A `409 Conflict` rolls back the optimistic state and renders a reconciliation strip in [`Inspector.jsx`](src/components/tasks/Inspector.jsx) so edits are never silently overwritten.
- **5-Second Deletion Grace Period with Undo:** Deleting a task or project optimistically removes it from the view and starts a 5-second countdown toast (`5s` $\rightarrow$ `1s`) with an **Undo** button and progress bar. The `DELETE` HTTP request is only sent after the 5-second grace window expires.
- **Project & Member Management:** Create projects ([`NewProjectSheet.jsx`](src/components/overlays/NewProjectSheet.jsx)), create tasks ([`NewTaskSheet.jsx`](src/components/overlays/NewTaskSheet.jsx)), manage project members and roles ([`MembersSheet.jsx`](src/components/overlays/MembersSheet.jsx)), and inspect per-project audit history (`GET /api/projects/{id}/activity`).

## Layout

```text
src/
  config.js              product name + USE_LIVE_API flag
  App.jsx                workspace state, routing, optimistic toggle, 5s delete grace timer
  api/
    client.js            fetch wrapper, Bearer token injection, 401 refresh retry, 409 conflict flag
    index.js             auth, project, member, activity, and task API functions
    fixtures.js          offline prototype fixtures (used only when USE_LIVE_API is false)
  lib/
    dates.js             due-date labels and overdue calculations
    roles.js             MANAGER → gold, MEMBER → blue, solo-project detection
  components/
    Icon.jsx  Stub.jsx
    auth/     AuthScreen
    shell/    Rail, ProjectGroup, AccountMenu, TopBar
    tasks/    TaskList, TaskRow, Inspector
    overlays/ Palette, ConfirmSheet, NewTaskSheet, NewProjectSheet, MembersSheet
  styles/                base, spine, auth, shell, overlays, responsive
```
