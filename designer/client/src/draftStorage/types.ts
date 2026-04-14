import type { ProcessName, ProcessVersionId } from "../components/Process/types";
import type { ScenarioGraph } from "../types/scenarioGraph";

type Id = ProcessName;
type VersionId = ProcessVersionId | null;

export interface Draft {
    id: Id;
    baseVersionId: VersionId;
    data: ScenarioGraph;
    updatedAt: string;
}

export interface DraftBackend {
    set: (id: Id, versionId: VersionId, value: Draft) => Promise<void>;
    remove: (id: Id, versionId: VersionId) => Promise<void>;
    list: (id: Id) => Promise<Array<{ versionId: VersionId; value: Draft }>>;
}
