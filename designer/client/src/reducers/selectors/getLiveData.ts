import { createSelector } from "reselect";

import type { NodeTransitionResult, NodeTransitionThroughputDto } from "../../http/resultsWithCountsDto";
import { getGraph, getSavedScenario, getScenario, isDeployed } from "./graph";
import { isGraphUpdated } from "./helpers";

export const getLiveData = createSelector(getGraph, (graph) => graph.liveData);

export const isReadyForLiveData = createSelector(getScenario, getSavedScenario, isDeployed, (scenario, savedScenario, isDeployed) => {
    if (!scenario?.name || scenario.isFragment || scenario.isArchived) return false;
    if (isGraphUpdated(scenario.scenarioGraph, savedScenario.scenarioGraph, true)) return false;
    return isDeployed;
});

export const getLiveDataRefresh = createSelector(getGraph, isReadyForLiveData, (g, readyForLiveData) => {
    if (!readyForLiveData) return null;
    return g.liveDataRefresh || null;
});

const EMPTY = [];
export const getNodeTransitionThroughput = createSelector(getLiveData, (liveData): NodeTransitionThroughputDto[] => {
    return liveData?.nodeTransitionThroughput || EMPTY;
});
export const getNodeTransitionResults = createSelector(getLiveData, (liveData): NodeTransitionResult[] => {
    return liveData?.results.nodeTransitionResults || EMPTY;
});
