/* eslint-disable i18next/no-literal-string */
import type { ProcessName } from "../components/Process/types";
import type { ScenarioDraft, ScenarioDraftStorage } from "./types";

const KEY_PREFIX = "nk.scenarioDraft.";

const key = (processName: ProcessName) => `${KEY_PREFIX}${processName}`;

export class LocalStorageDraftStorage implements ScenarioDraftStorage {
    async get(processName: ProcessName): Promise<ScenarioDraft | null> {
        const raw = localStorage.getItem(key(processName));
        if (!raw) return null;
        try {
            return JSON.parse(raw) as ScenarioDraft;
        } catch {
            localStorage.removeItem(key(processName));
            return null;
        }
    }

    async save(processName: ProcessName, draft: ScenarioDraft): Promise<void> {
        localStorage.setItem(key(processName), JSON.stringify(draft));
    }

    async delete(processName: ProcessName): Promise<void> {
        localStorage.removeItem(key(processName));
    }
}
