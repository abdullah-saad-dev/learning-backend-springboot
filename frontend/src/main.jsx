import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.jsx';

// Order matters: tokens first, then the shared spine, then per-area rules,
// then the breakpoints that override them.
import './styles/base.css';
import './styles/spine.css';
import './styles/auth.css';
import './styles/shell.css';
import './styles/overlays.css';
import './styles/responsive.css';

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
