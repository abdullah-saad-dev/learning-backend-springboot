import { useState, useEffect, useMemo, useRef } from 'react';
import { isLead, roleClass } from '../../lib/roles.js';

export default function Palette({ projects, onClose, onView, onLogout }) {
  const [query, setQuery] = useState('');
  const [active, setActive] = useState(0);
  const input = useRef(null);

  useEffect(() => { input.current?.focus(); }, []);

  const items = useMemo(() => {
    const all = [
      { key: 'mine', label: 'My tasks', cat: 'Go', run: () => onView({ type: 'mine' }) },
      { key: 'all', label: 'Everything', cat: 'Go', run: () => onView({ type: 'all' }) },
      ...projects.map((p) => ({
        key: p.id,
        label: p.name,
        cat: isLead(p.role) ? 'Lead' : 'Member',
        role: roleClass(p),
        run: () => onView({ type: 'project', id: p.id })
      })),
      { key: 'out', label: 'Log out of this device', cat: 'Account', run: () => onLogout('this') },
      { key: 'outall', label: 'Log out of all devices', cat: 'Account', run: () => onLogout('all') }
    ];
    const needle = query.trim().toLowerCase();
    return needle ? all.filter((x) => x.label.toLowerCase().includes(needle)) : all;
  }, [query, projects, onView, onLogout]);

  useEffect(() => { setActive(0); }, [query]);

  function onKeyDown(e) {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive((n) => Math.min(n + 1, items.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((n) => Math.max(n - 1, 0));
    } else if (e.key === 'Enter' && items[active]) {
      e.preventDefault();
      items[active].run();
      onClose();
    } else if (e.key === 'Escape') {
      onClose();
    }
  }

  return (
    <div className="scrim top" onClick={onClose}>
      <div className="sheet palette" role="dialog" aria-modal="true" aria-label="Command palette"
           onClick={(e) => e.stopPropagation()}>
        <input ref={input} value={query} aria-label="Command"
               placeholder="Jump to a project or run a command…"
               onChange={(e) => setQuery(e.target.value)} onKeyDown={onKeyDown} />

        {items.length === 0 ? (
          <p className="none">Nothing matches “{query}”.</p>
        ) : (
          <ul>
            {items.map((item, i) => (
              <li key={item.key}>
                <button data-active={i === active ? 'true' : 'false'}
                        onMouseEnter={() => setActive(i)}
                        onClick={() => { item.run(); onClose(); }}>
                  <i className={item.role || ''} />
                  {item.label}
                  <span className="cat">{item.cat}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
