import { useState, useEffect, useRef } from 'react';
import { initials } from '../../lib/roles.js';

/**
 * The signed-in user and the two ways out: this device, or all of them.
 *
 * They are separate items rather than one "Log out" because they are
 * different operations on the backend — one refresh-token family versus every
 * family the user owns — and the difference matters to someone who thinks
 * they left a session open on a shared machine.
 */
export default function AccountMenu({ me, onLogout }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    if (!open) return;
    const away = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    const esc = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', away);
    document.addEventListener('keydown', esc);
    return () => {
      document.removeEventListener('mousedown', away);
      document.removeEventListener('keydown', esc);
    };
  }, [open]);

  const pick = (fn) => () => { setOpen(false); fn(); };

  return (
    <div className="rail-foot" ref={ref}>
      {open && (
        <div className="menu" role="menu">
          <button role="menuitem" className="danger" onClick={pick(() => onLogout('this'))}>
            Log out of this device
          </button>
          <button role="menuitem" className="danger" onClick={pick(() => onLogout('all'))}>
            Log out of all devices
          </button>
        </div>
      )}

      <button className="me" onClick={() => setOpen((v) => !v)}
              aria-haspopup="menu" aria-expanded={open}>
        <span className="avatar">{initials(me.username)}</span>
        <span className="me-id">
          <b>{me.username}</b>
          <span>{me.email}</span>
        </span>
      </button>
    </div>
  );
}
