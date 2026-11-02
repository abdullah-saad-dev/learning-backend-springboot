import { useState, useEffect, useRef } from 'react';
import { initials } from '../../lib/roles.js';

/**
 * The signed-in user, profile details & photo launcher, and the two ways out:
 * this device, or all of them.
 */
export default function AccountMenu({ me, onOpenProfile, onLogout }) {
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
          {onOpenProfile && (
            <>
              <button role="menuitem" onClick={pick(onOpenProfile)}>
                View profile &amp; photo
              </button>
              <hr />
            </>
          )}
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
        <span className="avatar">
          {me.avatarUrl ? (
            <img src={me.avatarUrl} alt={me.username} />
          ) : (
            initials(me.username)
          )}
        </span>
        <span className="me-id">
          <b>{me.username}</b>
          <span>{me.bio || me.email}</span>
        </span>
        <span className="mono" style={{ marginLeft: 'auto', fontSize: '9.5px', color: 'var(--gold)' }}>
          Profile
        </span>
      </button>
    </div>
  );
}
