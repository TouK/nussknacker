import { createSelector } from "reselect";

import type { Initiator } from "../../actions/nk/liveData";
import { getNodeDetails } from "../../components/graph/node-modal/NodeDetailsContent/getNodeDetails";
import type { NodeTransitionResult } from "../../http/resultsWithCountsDto";
import type { LiveData } from "../graph/liveData";
import type { RootState } from "../index";
import { getGraph, getSavedScenario, getScenario, isCurrentVersionDeployed } from "./graph";
import { isGraphUpdated } from "./helpers";
import { getTestResults } from "./testing";

const EMPTY = [];
const getLiveData = (state: RootState): LiveData => state.liveData;

export const getVisibleDataType = createSelector(getGraph, (graph) => graph.visibleDataType || null);
export const isReadyForLiveData = createSelector(
    getScenario,
    getSavedScenario,
    getNodeDetails,
    isCurrentVersionDeployed,
    (scenario, savedScenario, nodeDetailsGetter, isDeployed) => {
        if (!isDeployed) return false;
        if (isGraphUpdated(scenario.scenarioGraph, savedScenario.scenarioGraph, "execution", nodeDetailsGetter)) return false;
        return true;
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
