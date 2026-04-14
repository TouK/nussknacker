import type { ProcessName, ProcessVersionId } from "../components/Process/types";
import type { ScenarioDraft } from "../draftStorage";
import { getProcessName, getProcessVersionId, getScenarioGraph } from "../reducers/selectors/graph";
import type { ScenarioGraph } from "../types/scenarioGraph";
import type { ThunkAction } from "./reduxTypes";

export type ScenarioDraftState = Record<string, ScenarioDraft>;
export type ScenarioDraftActions =
    | { type: "SCENARIO_DRAFT_SET"; payload: ScenarioDraft }
    | { type: "SCENARIO_DRAFT_CLEAR"; processName: ProcessName; baseVersionId: ProcessVersionId | null }
    | { type: "SCENARIO_DRAFT_HYDRATE"; drafts: ScenarioDraftState }
    | { type: "SCENARIO_VERSION_BUMPED"; processName: ProcessName; versionId: ProcessVersionId }
    | { type: "APPLY_SCENARIO_DRAFT"; scenarioGraph: ScenarioGraph };
export const scenarioDraftSet = (): ThunkAction => {
    return (dispatch, getState) => {
        const state = getState();
        const processName = getProcessName(state);
        const processVersionId = getProcessVersionId(state);
        const scenarioGraph = getScenarioGraph(state);
        dispatch({
            type: "SCENARIO_DRAFT_SET",
            payload: {
                id: processName,
                baseVersionId: processVersionId,
                data: scenarioGraph,
                updatedAt: new Date().toISOString(),
            },
        });
    };
};
export const scenarioDraftClear = (processName: ProcessName, baseVersionId: ProcessVersionId | null): ScenarioDraftActions => ({
    type: "SCENARIO_DRAFT_CLEAR",
    processName,
    baseVersionId,
});
export const scenarioDraftHydrate = (drafts: ScenarioDraftState): ScenarioDraftActions => ({ type: "SCENARIO_DRAFT_HYDRATE", drafts });
export const applyScenarioDraft = (scenarioGraph: ScenarioGraph): ScenarioDraftActions => ({
    type: "APPLY_SCENARIO_DRAFT",
    scenarioGraph,
});
export const scenarioVersionBumped = (processName: ProcessName, versionId: ProcessVersionId): ScenarioDraftActions => ({
    type: "SCENARIO_VERSION_BUMPED",
    processName,
    versionId,
});
