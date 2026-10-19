/** Product name. Change it here and it changes everywhere. */
export const PRODUCT = 'Keel';

/**
 * false → every API call resolves from src/api/fixtures.js.
 * true  → real requests go through Vite's proxy to Spring Boot.
 *
 * Flipping this is the whole migration. See src/api/index.js: each function
 * is marked REAL (the request is already written) or MOCK (it names the
 * endpoint it is waiting for and the shape it expects back).
 */
export const USE_LIVE_API = false;
