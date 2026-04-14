/* eslint-disable i18next/no-literal-string */
import type { ProcessName, ProcessVersionId } from "../components/Process/types";
import type { ScenarioDraft, ScenarioDraftBackend } from "./index";

const PREFIX = "nk.scenarioDraft.";
const SEP = "::";

const encode = (processName: ProcessName) => encodeURIComponent(processName);
const versionPart = (versionId: ProcessVersionId | null) => (versionId ?? "null").toString();

const buildKey = (processName: ProcessName, versionId: ProcessVersionId | null) =>
    `${PREFIX}${encode(processName)}${SEP}${versionPart(versionId)}`;

const scenarioPrefix = (processName: ProcessName) => `${PREFIX}${encode(processName)}${SEP}`;

const parseVersion = (raw: string): ProcessVersionId | null => (raw === "null" ? null : Number(raw));

export const localStorageBackend: ScenarioDraftBackend = {
    async set(processName, versionId, value) {
        localStorage.setItem(buildKey(processName, versionId), JSON.stringify(value));
    },
    async remove(processName, versionId) {
        localStorage.removeItem(buildKey(processName, versionId));
    },
    async list(processName) {
        const prefix = scenarioPrefix(processName);
        const entries: Array<{ versionId: ProcessVersionId | null; value: ScenarioDraft }> = [];
        for (let i = 0; i < localStorage.length; i++) {
            const k = localStorage.key(i);
            if (!k?.startsWith(prefix)) continue;
            const raw = localStorage.getItem(k);
            if (raw == null) continue;
            try {
                entries.push({
                    versionId: parseVersion(k.slice(prefix.length)),
                    value: JSON.parse(raw) as ScenarioDraft,
                });
            } catch {
                // skip corrupted entry
            }
        }
        return entries;
    },
};
