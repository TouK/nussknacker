import type { ProcessName, ProcessVersionId } from "../components/Process/types";

export interface ScenarioDraftBackend {
    get(processName: ProcessName, versionId: ProcessVersionId | null): Promise<string | null>;
    set(processName: ProcessName, versionId: ProcessVersionId | null, value: string): Promise<void>;
    remove(processName: ProcessName, versionId: ProcessVersionId | null): Promise<void>;
    list(processName: ProcessName): Promise<Array<{ versionId: ProcessVersionId | null; value: string }>>;
}
