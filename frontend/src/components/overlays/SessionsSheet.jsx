import { useState, useEffect } from 'react';
import * as api from '../../api/index.js';
import { fmtDate } from '../../lib/dates.js';

/**
 * One row per refresh-token family. This is what makes "log out everywhere"
 * mean something concrete rather than a button with no visible effect.
 */
export default function SessionsSheet({ onClose, onLogoutAll, notify }) {
  const [rows, setRows] = useState(null);

  useEffect(() => {
    let live = true;
    api.listSessions().then((s) => { if (live) setRows(s); });
    return () => { live = false; };
  }, []);

  function endSession(session) {
    setRows((all) => all.filter((r) => r.familyId !== session.familyId));
    notify(`Signed out of ${session.device}`);
  }

  return (
    <div className="scrim center" onClick={onClose}>
      <div className="sheet" role="dialog" aria-modal="true" aria-label="Active sessions"
           onClick={(e) => e.stopPropagation()}>
        <div className="sheet-head">
          <h2>Active sessions</h2>
          <p>
            One row per refresh-token family. Signing out of a device revokes its
            family; the access token it already holds expires within 15 minutes.
          </p>
        </div>

        <div className="sheet-body">
          {rows === null ? (
            <p className="mono" style={{ textTransform: 'none', letterSpacing: 0 }}>Loading…</p>
          ) : (
            rows.map((s) => (
              <div className="session" key={s.familyId}>
                <div>
                  <b>
                    {s.device}
                    {s.current && <span className="this">this device</span>}
                  </b>
                  <span className="mono" style={{ textTransform: 'none', letterSpacing: '.03em' }}>
                    {s.ip} · last used {fmtDate(s.lastSeenAt)}
                  </span>
                </div>
                <button onClick={() => endSession(s)}>Log out</button>
              </div>
            ))
          )}
        </div>

        <div className="sheet-foot">
          <button className="btn btn-ghost" onClick={onClose}>Close</button>
          <button className="btn btn-danger" onClick={onLogoutAll}>Log out of all devices</button>
        </div>
      </div>
    </div>
  );
}
