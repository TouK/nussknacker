import { localStorageBackend } from "./localStorageBackend";
import type { Draft, DraftBackend } from "./types";

export type ScenarioDraft = Draft;
export type ScenarioDraftBackend = DraftBackend;

// Swap this at bootstrap with a remote CRUD-backed implementation to persist drafts server-side.
let backend: ScenarioDraftBackend = localStorageBackend;

export const setScenarioDraftBackend = (next: typeof backend) => {
    backend = next;
};

export const getScenarioDraftBackend = () => backend;
