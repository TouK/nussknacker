/* eslint-disable i18next/no-literal-string */
import type { ProcessName, ProcessVersionId } from "../components/Process/types";
import type { ScenarioDraftBackend } from "./types";

const PREFIX = "nk.scenarioDraft.";
const SEP = "::";

const encode = (processName: ProcessName) => encodeURIComponent(processName);
const versionPart = (versionId: ProcessVersionId | null) => (versionId ?? "null").toString();

const buildKey = (processName: ProcessName, versionId: ProcessVersionId | null) =>
    `${PREFIX}${encode(processName)}${SEP}${versionPart(versionId)}`;

const scenarioPrefix = (processName: ProcessName) => `${PREFIX}${encode(processName)}${SEP}`;

const parseVersion = (raw: string): ProcessVersionId | null => (raw === "null" ? null : Number(raw));

export const localStorageBackend: ScenarioDraftBackend = {
    async get(processName, versionId) {
        return localStorage.getItem(buildKey(processName, versionId));
    },
    async set(processName, versionId, value) {
        localStorage.setItem(buildKey(processName, versionId), value);
    },
    async remove(processName, versionId) {
        localStorage.removeItem(buildKey(processName, versionId));
    },
    async list(processName) {
        const prefix = scenarioPrefix(processName);
        const entries: Array<{ versionId: ProcessVersionId | null; value: string }> = [];
        for (let i = 0; i < localStorage.length; i++) {
            const k = localStorage.key(i);
            if (!k?.startsWith(prefix)) continue;
            const value = localStorage.getItem(k);
            if (value == null) continue;
            entries.push({ versionId: parseVersion(k.slice(prefix.length)), value });
        }
        return entries;
    },
};
