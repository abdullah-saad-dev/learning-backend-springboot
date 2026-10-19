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
  const [mode, setMode] = useState('login');

  const [projects, setProjects] = useState([]);
  const [tasks, setTasks] = useState([]);

  const [view, setView] = useState({ type: 'mine' });
  const [filter, setFilter] = useState('open');
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState(null);
  const [conflictOn, setConflictOn] = useState(null);

  const [paletteOpen, setPaletteOpen] = useState(false);
  const [confirmScope, setConfirmScope] = useState(null);
  const [toast, setToast] = useState(null);

  const notify = useCallback((msg) => {
    setToast(msg);
    setTimeout(() => setToast(null), 2600);
  }, []);

  useEffect(() => {
    if (!me) return;
    let live = true;
    Promise.all([api.listProjects(), api.listTasks()]).then(([p, t]) => {
      if (!live) return;
      setProjects(p);
      setTasks(t);
    });
    return () => { live = false; };
  }, [me]);

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
   * Optimistic toggle. The version rides along with the request; a 409 means
   * the row moved underneath us, so the change is rolled back and the
   * inspector explains it instead of the edit vanishing silently.
   */
  const toggle = useCallback(async (task) => {
    setTasks((all) => all.map((t) => (t.id === task.id ? { ...t, done: !t.done } : t)));
    try {
      await api.setDone(task.id, !task.done, task.version);
    } catch (err) {
      setTasks((all) => all.map((t) => (t.id === task.id ? { ...t, done: task.done } : t)));
      if (err.conflict) {
        setSelected(task);
        setConflictOn(task.id);
      } else {
        notify('Could not save that change.');
      }
    }
  }, [notify]);

  const goTo = useCallback((v) => { setView(v); setSelected(null); }, []);

  async function doLogout(scope) {
    setConfirmScope(null);
    await (scope === 'all' ? api.logoutEverywhere() : api.logout());
    setMe(null);
    setMode('login');
    setProjects([]);
    setTasks([]);
    setSelected(null);
    setView({ type: 'mine' });
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
        onLogout={setConfirmScope}
      />

      <main className="main">
        <TopBar
          heading={heading} project={activeProject}
          query={query} onQuery={setQuery}
          filter={filter} onFilter={setFilter}
        />

        <div className={`board${selected ? ' with-panel' : ''}`}>
          <TaskList
            tasks={visible} projects={projects} selected={selected}
            onSelect={(t) => { setSelected(t); setConflictOn(null); }}
            onToggle={toggle}
            emptyFor={emptyCopyFor({ query, filter, view })}
          />

          {selected && (
            <Inspector
              task={selected}
              project={projectsById[selected.projectId]}
              conflict={conflictOn === selected.id}
              onResolve={() => { setConflictOn(null); notify('Reloaded from the server.'); }}
              onClose={() => { setSelected(null); setConflictOn(null); }}
            />
          )}
        </div>
      </main>

      {paletteOpen && (
        <Palette
          projects={projects}
          onClose={() => setPaletteOpen(false)}
          onView={goTo}
          onLogout={setConfirmScope}
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
