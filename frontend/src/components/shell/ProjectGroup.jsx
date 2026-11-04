import { roleClass, isSolo } from '../../lib/roles.js';

/**
 * One role group in the rail — "You lead" or "You are in".
 *
 * Each row puts a notch on the spine that runs down the whole rail. The notch
 * is the role; the count is open work; "solo" replaces the count because in a
 * one-person project the number says less than the fact.
 */
export default function ProjectGroup({ label, projects, view, openCountFor, onView }) {
  return (
    <div className="group">
      <div className="group-head">
        <span className="mono">{label}</span>
        <span className="mono">{projects.length}</span>
      </div>

      <div className="plist">
        {projects.map((p) => (
          <button
            key={p.id}
            className="prow"
            aria-current={view.type === 'project' && view.id === p.id ? 'true' : 'false'}
            onClick={() => onView({ type: 'project', id: p.id })}
          >
            <span className={`notch ${roleClass(p)}`} />
            <span className="pname">{p.name}</span>
            <span className="pmeta">
              {isSolo(p) ? <span className="solo-tag">solo</span> : openCountFor(p) || ''}
            </span>
          </button>
        ))}

        {projects.length === 0 && (
          <p className="mono"
             style={{ padding: '4px 16px 8px', textTransform: 'none', letterSpacing: 0 }}>
            Nothing here yet.
          </p>
        )}
      </div>
    </div>
  );
}
