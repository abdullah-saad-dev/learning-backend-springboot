import { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { USE_LIVE_API } from './config.js';
import * as api from './api/index.js';

import AuthScreen from './components/auth/AuthScreen.jsx';
import Rail from './components/shell/Rail.jsx';
import TopBar from './components/shell/TopBar.jsx';
import TaskList from './components/tasks/TaskList.jsx';
import ProjectsView from './components/projects/ProjectsView.jsx';
import Inspector from './components/tasks/Inspector.jsx';
import Palette from './components/overlays/Palette.jsx';
import ConfirmSheet from './components/overlays/ConfirmSheet.jsx';
import NewTaskSheet from './components/overlays/NewTaskSheet.jsx';
import NewProjectSheet from './components/overlays/NewProjectSheet.jsx';
import MembersSheet from './components/overlays/MembersSheet.jsx';
import ProfileSheet from './components/overlays/ProfileSheet.jsx';

const DELETE_GRACE_SECONDS = 5;

function emptyCopyFor({ query, filter, view, hasProjects }) {
  if (!hasProjects) {
    return {
      title: 'No projects yet',
      body: 'Create your first project to start organizing and assigning tasks.'
    };
  }
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
  const [projectRoleFilter, setProjectRoleFilter] = useState('all');
  const [query, setQuery] = useState('');
  const [selectedId, setSelectedId] = useState(null);
  const [conflictOn, setConflictOn] = useState(null);

  /**
   * Tasks whose done-state was toggled during the current view/filter session stay
   * visible in place (marked or unmarked) so an accidental press can be immediately
   * undone in-place. Switching views or re-opening the filter tab clears this set.
   */
  const [stickyTaskIds, setStickyTaskIds] = useState(() => new Set());

  const [paletteOpen, setPaletteOpen] = useState(false);
  const [newTaskOpen, setNewTaskOpen] = useState(false);
  const [newTaskProjectId, setNewTaskProjectId] = useState(null);
  const [newProjectOpen, setNewProjectOpen] = useState(false);
  const [membersOpen, setMembersOpen] = useState(false);
  const [membersProjectId, setMembersProjectId] = useState(null);
  const [profileOpen, setProfileOpen] = useState(false);
  const [confirmScope, setConfirmScope] = useState(null);
  const [toast, setToast] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);

  const toastTimerRef = useRef(null);
  const pendingDeleteRef = useRef(null);
  const tasksRef = useRef(tasks);
  tasksRef.current = tasks;
  const toggleChainsRef = useRef(new Map());

  const notify = useCallback((msg) => {
    if (toastTimerRef.current) clearTimeout(toastTimerRef.current);
    setToast(msg);
    toastTimerRef.current = setTimeout(() => setToast(null), 2600);
  }, []);

  const clearPendingTimers = useCallback(() => {
    const current = pendingDeleteRef.current;
    if (current) {
      if (current.timeoutId) clearTimeout(current.timeoutId);
      if (current.intervalId) clearInterval(current.intervalId);
    }
  }, []);

  const executeDelete = useCallback(async (entry) => {
    if (!entry) return;
    if (entry.type === 'task') {
      const task = entry.item;
      try {
        await api.deleteTask(task.id, task.version);
        notify('Task deleted.');
      } catch (err) {
        setTasks((all) => (all.some((t) => t.id === task.id) ? all : [task, ...all]));
        if (err.conflict) {
          setSelectedId(task.id);
          setConflictOn(task.id);
        } else {
          notify(err.detail || 'Could not delete task.');
        }
      }
    } else if (entry.type === 'project') {
      const project = entry.item;
      try {
        await api.deleteProject(project.id, project.version ?? 0);
        notify('Project deleted.');
      } catch (err) {
        setProjects((all) => (all.some((p) => p.id === project.id) ? all : [project, ...all]));
        if (entry.removedTasks?.length) {
          setTasks((all) => {
            const existingIds = new Set(all.map((t) => t.id));
            const restored = entry.removedTasks.filter((t) => !existingIds.has(t.id));
            return [...restored, ...all];
          });
        }
        setView({ type: 'project', id: project.id });
        notify(err.detail || 'Could not delete project.');
      }
    }
  }, [notify]);

  const scheduleDeleteTask = useCallback((task) => {
    if (pendingDeleteRef.current) {
      const prev = pendingDeleteRef.current;
      clearPendingTimers();
      pendingDeleteRef.current = null;
      setPendingDelete(null);
      executeDelete(prev);
    }

    setTasks((all) => all.filter((t) => t.id !== task.id));
    setSelectedId(null);

    const entry = {
      type: 'task',
      item: task,
      secondsLeft: DELETE_GRACE_SECONDS,
      timeoutId: null,
      intervalId: null
    };

    entry.intervalId = setInterval(() => {
      setPendingDelete((curr) =>
        curr && curr.item.id === task.id && curr.secondsLeft > 1
          ? { ...curr, secondsLeft: curr.secondsLeft - 1 }
          : curr
      );
    }, 1000);

    entry.timeoutId = setTimeout(() => {
      if (entry.intervalId) clearInterval(entry.intervalId);
      pendingDeleteRef.current = null;
      setPendingDelete(null);
      executeDelete(entry);
    }, DELETE_GRACE_SECONDS * 1000);

    pendingDeleteRef.current = entry;
    setPendingDelete({
      type: 'task',
      item: task,
      secondsLeft: DELETE_GRACE_SECONDS
    });
  }, [clearPendingTimers, executeDelete]);

  const scheduleDeleteProject = useCallback((project) => {
    if (pendingDeleteRef.current) {
      const prev = pendingDeleteRef.current;
      clearPendingTimers();
      pendingDeleteRef.current = null;
      setPendingDelete(null);
      executeDelete(prev);
    }

    const removedTasks = tasks.filter((t) => t.projectId === project.id);
    setMembersOpen(false);
    setProjects((all) => all.filter((p) => p.id !== project.id));
    setTasks((all) => all.filter((t) => t.projectId !== project.id));
    setView({ type: 'mine' });
    setSelectedId(null);

    const entry = {
      type: 'project',
      item: project,
      removedTasks,
      secondsLeft: DELETE_GRACE_SECONDS,
      timeoutId: null,
      intervalId: null
    };

    entry.intervalId = setInterval(() => {
      setPendingDelete((curr) =>
        curr && curr.item.id === project.id && curr.secondsLeft > 1
          ? { ...curr, secondsLeft: curr.secondsLeft - 1 }
          : curr
      );
    }, 1000);

    entry.timeoutId = setTimeout(() => {
      if (entry.intervalId) clearInterval(entry.intervalId);
      pendingDeleteRef.current = null;
      setPendingDelete(null);
      executeDelete(entry);
    }, DELETE_GRACE_SECONDS * 1000);

    pendingDeleteRef.current = entry;
    setPendingDelete({
      type: 'project',
      item: project,
      secondsLeft: DELETE_GRACE_SECONDS
    });
  }, [tasks, clearPendingTimers, executeDelete]);

  const undoPendingDelete = useCallback(() => {
    const entry = pendingDeleteRef.current;
    if (!entry) return;
    clearPendingTimers();
    pendingDeleteRef.current = null;
    setPendingDelete(null);

    if (entry.type === 'task') {
      const task = entry.item;
      setTasks((all) => (all.some((t) => t.id === task.id) ? all : [task, ...all]));
      setSelectedId(task.id);
      notify('Task deletion undone.');
    } else if (entry.type === 'project') {
      const project = entry.item;
      setProjects((all) => (all.some((p) => p.id === project.id) ? all : [project, ...all]));
      if (entry.removedTasks?.length) {
        setTasks((all) => {
          const existingIds = new Set(all.map((t) => t.id));
          const restored = entry.removedTasks.filter((t) => !existingIds.has(t.id));
          return [...restored, ...all];
        });
      }
      setView({ type: 'project', id: project.id });
      notify('Project deletion undone.');
    }
  }, [clearPendingTimers, notify]);

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
    if (filter === 'open') list = list.filter((t) => !t.done || stickyTaskIds.has(t.id));
    if (filter === 'done') list = list.filter((t) => t.done || stickyTaskIds.has(t.id));
    const needle = query.trim().toLowerCase();
    if (needle) list = list.filter((t) => t.title.toLowerCase().includes(needle));
    return list;
  }, [tasks, view, filter, query, me, stickyTaskIds]);

  /**
   * Optimistic toggle with per-task request serialization and in-place sticky visibility:
   * - Keeping the toggled task ID in `stickyTaskIds` prevents the row from vanishing
   *   immediately out of the "Open" or "Done" list, so the user sees it marked in its
   *   place and can click again if they pressed it by accident.
   * - Chaining requests per task ensures a fast double-click (mark -> unmark) uses the
   *   server's bumped version from the first call instead of failing with a 409.
   */
  const toggle = useCallback((task) => {
    const nextDone = !task.done;
    setStickyTaskIds((prev) => {
      const next = new Set(prev);
      next.add(task.id);
      return next;
    });
    setTasks((all) => all.map((t) => (t.id === task.id ? { ...t, done: nextDone } : t)));

    const prevChain = toggleChainsRef.current.get(task.id) || Promise.resolve();
    const nextChain = prevChain.then(async () => {
      const latest = tasksRef.current.find((t) => t.id === task.id);
      const versionToUse = latest ? latest.version : task.version;
      try {
        const updated = await api.setDone(task.id, nextDone, versionToUse);
        if (updated && typeof updated.version === 'number') {
          setTasks((all) => all.map((t) => (t.id === task.id ? { ...t, ...updated } : t)));
        }
      } catch (err) {
        setTasks((all) => all.map((t) => (t.id === task.id ? { ...t, done: !nextDone } : t)));
        if (err.conflict) {
          setSelectedId(task.id);
          setConflictOn(task.id);
        } else {
          notify(err.detail || 'Could not save that change.');
        }
      }
    });
    toggleChainsRef.current.set(task.id, nextChain);
  }, [notify]);

  const goTo = useCallback((v) => {
    setView(v);
    setSelectedId(null);
    setStickyTaskIds(new Set());
  }, []);

  const handleFilterChange = useCallback((nextFilter) => {
    setFilter(nextFilter);
    setStickyTaskIds(new Set());
  }, []);

  const handleOpenNewTask = useCallback((defaultProjId = null) => {
    if (projects.length === 0) {
      notify('Create a project first to add tasks to it.');
      setNewProjectOpen(true);
      return;
    }
    setNewTaskProjectId(typeof defaultProjId === 'string' ? defaultProjId : null);
    setNewTaskOpen(true);
  }, [projects.length, notify]);

  async function doLogout(scope) {
    setConfirmScope(null);
    clearPendingTimers();
    pendingDeleteRef.current = null;
    setPendingDelete(null);
    setStickyTaskIds(new Set());
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

  const isProjectsView = view.type === 'projects';
  const heading =
    view.type === 'mine' ? 'Assigned to me'
    : view.type === 'all' ? 'All workspace tasks'
    : view.type === 'projects' ? 'My Projects'
    : activeProject ? activeProject.name
    : 'Project';

  const membersProject = (membersProjectId && projectsById[membersProjectId]) || activeProject;

  return (
    <div className="shell">
      <Rail
        me={me} projects={projects} tasks={tasks} view={view}
        onView={goTo}
        onNewTask={() => handleOpenNewTask(activeProject?.id)}
        onNewProject={() => setNewProjectOpen(true)}
        onOpenProfile={() => setProfileOpen(true)}
        onLogout={setConfirmScope}
      />

      <main className="main">
        <TopBar
          heading={heading}
          isProjectsView={isProjectsView}
          project={activeProject}
          query={query} onQuery={setQuery}
          filter={filter} onFilter={handleFilterChange}
          projectRoleFilter={projectRoleFilter}
          onProjectRoleFilter={setProjectRoleFilter}
          onNewTask={() => handleOpenNewTask(activeProject?.id)}
          onNewProject={() => setNewProjectOpen(true)}
          onOpenMembers={activeProject ? () => {
            setMembersProjectId(activeProject.id);
            setMembersOpen(true);
          } : null}
        />

        <div className={`board${!isProjectsView && selected ? ' with-panel' : ''}`}>
          {isProjectsView ? (
            <ProjectsView
              projects={projects}
              tasks={tasks}
              query={query}
              roleFilter={projectRoleFilter}
              onOpenProject={(projId) => goTo({ type: 'project', id: projId })}
              onOpenMembers={(proj) => {
                setMembersProjectId(proj.id);
                setMembersOpen(true);
              }}
              onNewProject={() => setNewProjectOpen(true)}
              onNewTaskForProject={(projId) => handleOpenNewTask(projId)}
            />
          ) : (
            <TaskList
              tasks={visible} projects={projects} selected={selected}
              onSelect={(t) => { setSelectedId(t.id); setConflictOn(null); }}
              onToggle={toggle}
              onNewTask={() => handleOpenNewTask(activeProject?.id)}
              onNewProject={() => setNewProjectOpen(true)}
              emptyFor={emptyCopyFor({ query, filter, view, hasProjects: projects.length > 0 })}
            />
          )}

          {!isProjectsView && selected && (
            <Inspector
              task={selected}
              project={projectsById[selected.projectId]}
              me={me}
              conflict={conflictOn === selected.id}
              onConflict={(id) => setConflictOn(id)}
              onToggle={toggle}
              onResolve={async () => {
                await reloadData();
                setConflictOn(null);
                notify('Reloaded from the server.');
              }}
              onUpdated={(updated) => {
                setTasks((all) => all.map((t) => (t.id === updated.id ? updated : t)));
                notify('Task saved.');
              }}
              onDeleted={scheduleDeleteTask}
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
          onNewTask={() => handleOpenNewTask(activeProject?.id)}
          onNewProject={() => setNewProjectOpen(true)}
          onLogout={setConfirmScope}
        />
      )}

      {newTaskOpen && (
        <NewTaskSheet
          projects={projects}
          defaultProjectId={newTaskProjectId || activeProject?.id}
          onClose={() => { setNewTaskOpen(false); setNewTaskProjectId(null); }}
          onCreated={(created) => {
            setTasks((all) => [created, ...all]);
            setNewTaskOpen(false);
            setNewTaskProjectId(null);
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
            setStickyTaskIds(new Set());
            notify('Project created.');
          }}
        />
      )}

      {membersOpen && membersProject && (
        <MembersSheet
          project={membersProject}
          me={me}
          onClose={() => { setMembersOpen(false); setMembersProjectId(null); }}
          onChanged={() => { reloadData(); }}
          onDeleted={scheduleDeleteProject}
        />
      )}

      {profileOpen && (
        <ProfileSheet
          me={me}
          projects={projects}
          tasks={tasks}
          onClose={() => setProfileOpen(false)}
          onSaved={(updatedMe) => {
            setMe(updatedMe);
            setProfileOpen(false);
            notify('Profile updated.');
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

      {pendingDelete ? (
        <div className="toast toast-undo" role="status" aria-live="polite">
          <span>
            Deleting {pendingDelete.type} <b>“{pendingDelete.item.title || pendingDelete.item.name}”</b>
          </span>
          <span className="toast-timer">{pendingDelete.secondsLeft}s</span>
          <button type="button" className="toast-btn" onClick={undoPendingDelete}>
            Undo
          </button>
          <span key={pendingDelete.item.id} className="toast-bar" />
        </div>
      ) : (
        toast && <div className="toast" role="status">{toast}</div>
      )}

      {/* Only while the data is fake. It disappears on its own when the flag flips. */}
      {!USE_LIVE_API && (
        <div className="ribbon"><i />Design prototype · mock data</div>
      )}
    </div>
  );
}
