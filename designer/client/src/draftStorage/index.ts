import { localStorageBackend } from "./localStorageBackend";
import type { ScenarioDraftBackend } from "./types";

// Swap this at bootstrap with a remote CRUD-backed implementation to persist drafts server-side.
let backend: ScenarioDraftBackend = localStorageBackend;

export const setScenarioDraftBackend = (next: ScenarioDraftBackend) => {
    backend = next;
};

export const getScenarioDraftBackend = (): ScenarioDraftBackend => backend;

export type { ScenarioDraftBackend } from "./types";
