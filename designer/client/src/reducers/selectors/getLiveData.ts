import { createSelector } from "reselect";

import type { Initiator } from "../../actions/nk/liveData";
import type { NodeTransitionResult } from "../../http/resultsWithCountsDto";
import type { RootState } from "../index";
import { getGraph, getSavedScenario, getScenario, getTestResults, isCurrentVersionDeployed } from "./graph";
import { isGraphUpdated } from "./helpers";

const EMPTY = [];
const getLiveData = (state: RootState) => state.liveData;

export const getVisibleDataType = createSelector(getGraph, (graph) => graph.visibleDataType || null);
export const isReadyForLiveData = createSelector(
    getScenario,
    getSavedScenario,
    isCurrentVersionDeployed,
    (scenario, savedScenario, isDeployed) => {
        if (!scenario?.name || scenario.isFragment || scenario.isArchived) return false;
        if (isGraphUpdated(scenario.scenarioGraph, savedScenario.scenarioGraph, true)) return false;
        return isDeployed;
    },
);

export const getIsLiveDataWorking = createSelector(getLiveData, ({ working }) => working || false);
export const getLiveDataLastUpdate = createSelector(getLiveData, ({ last }) => last || null);
export const getLiveDataNextUpdate = createSelector(getLiveData, ({ nextIn }) => nextIn || null);
export const getPauseReasons = createSelector(getLiveData, (data): Initiator[] => data.pauseReasons || EMPTY);
export const getHasPauseReasons = createSelector(getPauseReasons, (pauseReasons) => pauseReasons.length);

export const getNodeTransitionResults = createSelector(getTestResults, (testResults): NodeTransitionResult[] => {
    return testResults?.nodeTransitionResults || EMPTY;
});
