import { isEmpty, isEqual } from "lodash";
import { createSelector } from "reselect";

import type { TestFormParameters } from "../../common/TestResultUtils";
import { getHasPendingChanges } from "../../components/graph/node-modal/node/useEditState";
import { hasNodeIdPlaceholder } from "../../components/graph/node-modal/nodeIdFieldHelpers";
import ProcessStateUtils from "../../components/Process/ProcessStateUtils";
import type { Scenario } from "../../components/Process/types";
import { isStatusRunning } from "../../components/Process/types";
import { ScenarioGraphSourceType } from "../../http/HttpService/types";
import type { ProcessCounts } from "../../http/resultsWithCountsDto";
import type { ScenarioGraph } from "../../types/scenarioGraph";
import type { RootState } from "../index";
import { getHistoryPast } from "./getHistory";
import { areLabelsUpdated, isGraphUpdated } from "./helpers";
import { getProcessState } from "./scenarioState";

export const getGraph = (state: RootState) => state.graphReducer.present;
export const getScenarioLoading = createSelector(getGraph, (g) => g.scenarioLoading);

export const getScenario = createSelector(getGraph, (g) => g.scenario);
export const getSavedScenario = createSelector(getHistoryPast, getScenario, (past, scenario): Scenario => past?.[0]?.scenario || scenario);

export const getScenarioGraph = createSelector(getGraph, (g) => g.scenario.scenarioGraph || ({} as ScenarioGraph), {
    memoizeOptions: {
        resultEqualityCheck: isEqual,
    },
});

export const getNodes = createSelector(getScenarioGraph, (g) => g.nodes);

export const getScenarioLabels = createSelector(getGraph, (g) => g.scenario.labels || []);

export const getProcessName = createSelector(getScenario, (d) => d?.name);
export const getProcessUnsavedNewName = createSelector(getScenarioGraph, (g) => g.properties?.name);
export const getUnsavedOrCurrentName = createSelector(getProcessName, getProcessUnsavedNewName, (currentName, unsavedNewName) => {
    return unsavedNewName || currentName;
});
export const isProcessRenamed = createSelector(
    getProcessName,
    getUnsavedOrCurrentName,
    (currentName, unsavedNewName) => unsavedNewName !== currentName,
);

export const getProcessVersionId = createSelector(getScenario, (d) => d?.processVersionId || null);
export const getProcessCategory = createSelector(getScenario, (d) => d?.processCategory || "");
export const getProcessingType = createSelector(getScenario, (d) => d?.processingType);
export const isLatestProcessVersion = createSelector(getScenario, (d) => d?.isLatestVersion);
export const isFragment = createSelector(getScenario, (p) => p?.isFragment);
export const isArchived = createSelector(getScenario, (p) => p?.isArchived);
export const isPristine = createSelector(
    getScenario,
    isProcessRenamed,
    getSavedScenario,
    getHasPendingChanges,
    (scenario, isProcessRenamed, savedScenario, hasPendingChanges) => {
        if (isEmpty(scenario)) return true;
        if (isProcessRenamed) return false;
        if (areLabelsUpdated(scenario.labels, savedScenario.labels)) return false;
        if (isGraphUpdated(scenario.scenarioGraph, savedScenario.scenarioGraph)) return false;
        if (hasPendingChanges) return false;
        return true;
    },
);

export const getSelectionState = createSelector(getGraph, (g) => g.selectionState);
export const canModifySelectedNodes = createSelector(getSelectionState, (s) => !isEmpty(s));
export const getProcessNodesIds = createSelector(getNodes, (nodes) => nodes.map((n) => n.id));
export const getProcessNodesNames = createSelector(getNodes, (nodes) => nodes.map((n) => n.name));
export const hasNodeWithIdPlaceholder = createSelector(getProcessNodesNames, (nodes) => nodes?.some(hasNodeIdPlaceholder));
export const isSaveDisabled = createSelector(
    [isPristine, isLatestProcessVersion, hasNodeWithIdPlaceholder],
    (pristine, latest, hasNodeWithIdPlaceholder) => (pristine && latest) || hasNodeWithIdPlaceholder,
);
export const isDeployVisible = createSelector([getProcessState], (state) => ProcessStateUtils.canSeeDeploy(state));
export const isRedeployVisible = createSelector([getProcessState], (state) => ProcessStateUtils.canSeeRedeploy(state));
export const isCancelPossible = createSelector(getProcessState, (state) => ProcessStateUtils.canCancel(state));
export const isRunOffScheduleVisible = createSelector([getProcessState], (state) => ProcessStateUtils.canSeeRunOffSchedule(state));
export const isArchivePossible = createSelector(
    [getProcessState, isFragment],
    (state, isFragment) => isFragment || ProcessStateUtils.canArchive(state),
);
export const getTestCapabilities = createSelector(getGraph, (g) => g.testCapabilities);
export const getTestParameters = createSelector(getGraph, (g) => g.testFormParameters || ([] as TestFormParameters[]));
export const getProcessCountsRefresh = createSelector(getGraph, (g) => g.processCountsRefresh || null);
export const getProcessCounts = createSelector(getGraph, (g): ProcessCounts => g.processCounts || ({} as ProcessCounts));

export const getVersions = createSelector(getScenario, (details) => details?.history || []);
export const hasOneVersion = createSelector(getVersions, (h) => h.length <= 1);
export const getProperties = createSelector(getProcessName, getScenarioGraph, (name, graph) => {
    return { name, ...graph.properties };
});
export const getAdditionalFields = createSelector(getProperties, (p) => p?.additionalFields);
export const getScenarioDescription = createSelector(getAdditionalFields, (f): [string, boolean] => [f?.description, f?.showDescription]);

export const getLayout = createSelector(getGraph, (state) => state.layout || []);
export const getRunningVersion = createSelector(getProcessState, (state) => {
    return isStatusRunning(state?.status) ? state.status.versionId : null;
});
export const isCurrentVersionDeployed = createSelector(getProcessVersionId, getRunningVersion, (version, runningVersion) => {
    return runningVersion === `${version}`;
});

export const getScenarioGraphSource = createSelector([getGraph, getScenarioLabels, getProcessVersionId], (graph, labels, versionId) => {
    return {
        type: ScenarioGraphSourceType.FROM_GRAPH,
        scenarioGraph: graph?.scenario?.scenarioGraph,
        scenarioLabels: labels,
        baseScenarioVersionId: versionId,
    };
});
