/**
 * Role is the one fact this interface is built around, so the mapping from
 * data to colour lives in exactly one place.
 *
 *   LEADER → gold    you lead it
 *   MEMBER → blue    you are in it
 */

export const isLead = (role) => role === 'LEADER' || role === 'MANAGER';

export const roleClass = (project) =>
  project && isLead(project.role) ? 'lead' : 'member';

export const roleLabel = (project) =>
  project && isLead(project.role) ? (project.role === 'MANAGER' ? 'Manager' : 'You lead') : 'Member';

/** A project you are alone in. Every task in it is yours automatically. */
export const isSolo = (project) => !!project && project.memberCount === 1;

export const initials = (name) =>
  (name || '?').split(/\s+/).slice(0, 2).map((w) => w[0]).join('').toUpperCase();
