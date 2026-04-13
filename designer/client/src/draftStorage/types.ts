import type { ProcessName, ProcessVersionId } from "../components/Process/types";
import type { ScenarioGraph } from "../types/scenarioGraph";

export interface ScenarioDraft {
    scenarioGraph: ScenarioGraph;
    baseVersionId: ProcessVersionId | null;
    updatedAt: string;
}

export interface ScenarioDraftStorage {
    get(processName: ProcessName): Promise<ScenarioDraft | null>;
    save(processName: ProcessName, draft: ScenarioDraft): Promise<void>;
    delete(processName: ProcessName): Promise<void>;
}
