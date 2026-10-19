/**
 * A slot the backend has not filled yet.
 *
 * Deliberately visible and specific: it names the missing column or table so
 * the gap is a to-do rather than a mystery. Never fake the feature instead.
 */
export default function Stub({ children }) {
  return (
    <div className="stub">
      <span className="mono">Waiting on the backend</span>
      <p>{children}</p>
    </div>
  );
}
