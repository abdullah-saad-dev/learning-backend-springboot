import { useState, useEffect, useMemo, useCallback } from 'react';
import { USE_LIVE_API } from './config.js';
import * as api from './api/index.js';

import AuthScreen from './components/auth/AuthScreen.jsx';
import Rail from './components/shell/Rail.jsx';
import TopBar from './components/shell/TopBar.jsx';
import TaskList from './components/tasks/TaskList.jsx';
import Inspector from './components/tasks/Inspector.jsx';
import Palette from './components/overlays/Palette.jsx';
import ConfirmSheet from './components/overlays/ConfirmSheet.jsx';
import NewTaskSheet from './components/overlays/NewTaskSheet.jsx';
import NewProjectSheet from './components/overlays/NewProjectSheet.jsx';
import MembersSheet from './components/overlays/MembersSheet.jsx';

function emptyCopyFor({ query, filter, view }) {
  if (query) {
    return { title: 'No task matches that search',
             body: 'Try a shorter word, or clear the search to see the whole list.' };
  }
  if (filter === 'done') {
    return { title: 'Nothing finished here yet',
             body: 'Completed tasks collect here as you close them.' };
  }
  if (view.type === 'mine') {
    return { title: 'No open tasks assigned to you',
             body: 'Tasks you are assigned across every project land here.' };
  }
  return { title: 'No open tasks', body: 'Nothing in this project is waiting on anyone.' };
}

export default function App() {
  const [me, setMe] = useState(null);
  const [booting, setBooting] = useState(USE_LIVE_API);
  const [mode, setMode] = useState('login');

  const [projects, setProjects] = useState([]);
  const [tasks, setTasks] = useState([]);

  const [view, setView] = useState({ type: 'mine' });
  const [filter, setFilter] = useState('open');
  const [query, setQuery] = useState('');
  const [selectedId, setSelectedId] = useState(null);
  const [conflictOn, setConflictOn] = useState(null);

  const [paletteOpen, setPaletteOpen] = useState(false);
  const [newTaskOpen, setNewTaskOpen] = useState(false);
  const [newProjectOpen, setNewProjectOpen] = useState(false);
  const [membersOpen, setMembersOpen] = useState(false);
  const [confirmScope, setConfirmScope] = useState(null);
  const [toast, setToast] = useState(null);

  const notify = useCallback((msg) => {
    setToast(msg);
    setTimeout(() => setToast(null), 2600);
  }, []);

  // Attempt silent session restoration from the HttpOnly refresh cookie on initial load
  useEffect(() => {
    if (!USE_LIVE_API) return;
    let live = true;
    api.refresh()
      .then((user) => { if (live) setMe(user); })
      .catch(() => {})
      .finally(() => { if (live) setBooting(false); });
    return () => { live = false; };
  }, []);

  const reloadData = useCallback(async () => {
    const [p, t] = await Promise.all([api.listProjects(), api.listTasks()]);
    setProjects(p);
    setTasks(t);
    return { projects: p, tasks: t };
  }, []);

  useEffect(() => {
    if (!me) return;
    let live = true;
    Promise.all([api.listProjects(), api.listTasks()])
      .then(([p, t]) => {
        if (!live) return;
        setProjects(p);
        setTasks(t);
      })
      .catch(() => {
        if (live) notify('Could not load workspace data.');
      });
    return () => { live = false; };
  }, [me, notify]);

  useEffect(() => {
    if (!me) return;
    const onKey = (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setPaletteOpen((v) => !v);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [me]);

  const projectsById = useMemo(
    () => Object.fromEntries(projects.map((p) => [p.id, p])),
    [projects]
  );

  const selected = useMemo(
    () => tasks.find((t) => t.id === selectedId) || null,
    [tasks, selectedId]
  );

  const activeProject = view.type === 'project' ? projectsById[view.id] : null;

  const visible = useMemo(() => {
    let list = tasks;
    if (view.type === 'mine') list = list.filter((t) => t.assigneeId === me?.id);
    if (view.type === 'project') list = list.filter((t) => t.projectId === view.id);
    if (filter === 'open') list = list.filter((t) => !t.done);
    if (filter === 'done') list = list.filter((t) => t.done);
    const needle = query.trim().toLowerCase();
    if (needle) list = list.filter((t) => t.title.toLowerCase().includes(needle));
    return list;
  }, [tasks, view, filter, query, me]);

  /**
   * Optimistic toggle. The version rides along with the request; when the server
   * responds we store the updated task (with its bumped version). A 409 means
   * the row moved underneath us, so the change is rolled back and the inspector
   * explains it instead of the edit vanishing silently.
   */
  const toggle = useCallback(async (task) => {
    setTasks((all) => all.map((t) => (t.id === task.id ? { ...t, done: !t.done } : t)));
    try {
      const updated = await api.setDone(task.id, !task.done, task.version);
      if (updated && typeof updated.version === 'number') {
        setTasks((all) => all.map((t) => (t.id === task.id ? { ...t, ...updated } : t)));
      }
    } catch (err) {
      setTasks((all) => all.map((t) => (t.id === task.id ? { ...t, done: task.done } : t)));
      if (err.conflict) {
        setSelectedId(task.id);
        setConflictOn(task.id);
      } else {
        notify(err.detail || 'Could not save that change.');
      }
    }
  }, [notify]);

  const goTo = useCallback((v) => { setView(v); setSelectedId(null); }, []);

  async function doLogout(scope) {
    setConfirmScope(null);
    try {
      await (scope === 'all' ? api.logoutEverywhere() : api.logout());
    } catch {
      notify('Could not sign out completely.');
      return;
    }
    setMe(null);
    setMode('login');
    setProjects([]);
    setTasks([]);
    setSelectedId(null);
    setView({ type: 'mine' });
  }

  if (booting) {
    return null;
  }

  if (!me) {
    return <AuthScreen mode={mode} onMode={setMode} onDone={setMe} />;
  }

  const heading =
    view.type === 'mine' ? 'My tasks'
    : view.type === 'all' ? 'Everything'
    : activeProject ? activeProject.name
    : 'Project';

  return (
    <div className="shell">
      <Rail
        me={me} projects={projects} tasks={tasks} view={view}
        onView={goTo}
        onNewProject={() => setNewProjectOpen(true)}
        onLogout={setConfirmScope}
      />

      <main className="main">
        <TopBar
          heading={heading} project={activeProject}
          query={query} onQuery={setQuery}
          filter={filter} onFilter={setFilter}
          onNewTask={projects.length > 0 ? () => setNewTaskOpen(true) : null}
          onOpenMembers={activeProject ? () => setMembersOpen(true) : null}
        />

        <div className={`board${selected ? ' with-panel' : ''}`}>
          <TaskList
            tasks={visible} projects={projects} selected={selected}
            onSelect={(t) => { setSelectedId(t.id); setConflictOn(null); }}
            onToggle={toggle}
            emptyFor={emptyCopyFor({ query, filter, view })}
          />

          {selected && (
            <Inspector
              task={selected}
              project={projectsById[selected.projectId]}
              me={me}
              conflict={conflictOn === selected.id}
              onConflict={(id) => setConflictOn(id)}
              onResolve={async () => {
                await reloadData();
                setConflictOn(null);
                notify('Reloaded from the server.');
              }}
              onUpdated={(updated) => {
                setTasks((all) => all.map((t) => (t.id === updated.id ? updated : t)));
                notify('Task saved.');
              }}
              onDeleted={(id) => {
                setTasks((all) => all.filter((t) => t.id !== id));
                setSelectedId(null);
                notify('Task deleted.');
              }}
              onClose={() => { setSelectedId(null); setConflictOn(null); }}
            />
          )}
        </div>
      </main>

      {paletteOpen && (
        <Palette
          projects={projects}
          onClose={() => setPaletteOpen(false)}
          onView={goTo}
          onNewTask={projects.length > 0 ? () => setNewTaskOpen(true) : null}
          onNewProject={() => setNewProjectOpen(true)}
          onLogout={setConfirmScope}
        />
      )}

      {newTaskOpen && (
        <NewTaskSheet
          projects={projects}
          defaultProjectId={activeProject?.id}
          onClose={() => setNewTaskOpen(false)}
          onCreated={(created) => {
            setTasks((all) => [created, ...all]);
            setNewTaskOpen(false);
            setSelectedId(created.id);
            notify('Task created.');
          }}
        />
      )}

      {newProjectOpen && (
        <NewProjectSheet
          onClose={() => setNewProjectOpen(false)}
          onCreated={(created) => {
            setProjects((all) => [created, ...all]);
            setNewProjectOpen(false);
            setView({ type: 'project', id: created.id });
            setSelectedId(null);
            notify('Project created.');
          }}
        />
      )}

      {membersOpen && activeProject && (
        <MembersSheet
          project={activeProject}
          me={me}
          onClose={() => setMembersOpen(false)}
          onChanged={() => { reloadData(); }}
          onDeleted={(deletedId) => {
            setMembersOpen(false);
            setProjects((all) => all.filter((p) => p.id !== deletedId));
            setTasks((all) => all.filter((t) => t.projectId !== deletedId));
            setView({ type: 'mine' });
            setSelectedId(null);
            notify('Project deleted.');
          }}
        />
      )}

      {confirmScope && (
        <ConfirmSheet
          scope={confirmScope}
          onCancel={() => setConfirmScope(null)}
          onConfirm={() => doLogout(confirmScope)}
        />
      )}

      {toast && <div className="toast" role="status">{toast}</div>}

      {/* Only while the data is fake. It disappears on its own when the flag flips. */}
      {!USE_LIVE_API && (
        <div className="ribbon"><i />Design prototype · mock data</div>
      )}
    </div>
  );
}
