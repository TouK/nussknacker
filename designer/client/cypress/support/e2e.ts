// ***********************************************************
// This example support/index.js is processed and
// loaded automatically before your test files.
//
// This is a great place to put global configuration and
// behavior that modifies Cypress.
//
// You can change the location of this file or turn off
// automatically serving support files with the
// 'supportFile' configuration option.
//
// You can read more here:
// https://on.cypress.io/configuration
// ***********************************************************

// Import commands.js using ES2015 syntax:
import "./commands";

// useRouteLeavingGuard registers a beforeunload handler that prevents navigation
// when there are unsaved changes. This blocks Cypress from cleaning up between tests.
Cypress.on("window:before:unload", (e) => e.stopImmediatePropagation());
