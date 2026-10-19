/**
 * Logging out of everything is the one action here with a blast radius, so
 * the copy states it plainly instead of asking "are you sure?".
 */
export default function ConfirmSheet({ scope, onCancel, onConfirm }) {
  const everywhere = scope === 'all';

  return (
    <div className="scrim center" onClick={onCancel}>
      <div className="sheet" style={{ maxWidth: '400px' }} role="dialog" aria-modal="true"
           onClick={(e) => e.stopPropagation()}>
        <div className="sheet-head">
          <h2>{everywhere ? 'Log out of all devices?' : 'Log out of this device?'}</h2>
          <p>
            {everywhere
              ? 'Every session ends, including this one. Anything still holding an access token loses it within 15 minutes.'
              : 'Only this browser is signed out. Your other devices stay signed in.'}
          </p>
        </div>
        <div className="sheet-foot">
          <button className="btn btn-ghost" onClick={onCancel}>Cancel</button>
          <button className="btn btn-danger" onClick={onConfirm}>
            {everywhere ? 'Log out everywhere' : 'Log out'}
          </button>
        </div>
      </div>
    </div>
  );
}
