import { isEmpty, isEqual } from "lodash";
import { createSelector } from "reselect";

import ProcessUtils from "../../common/ProcessUtils";
import type { TestFormParameters } from "../../common/TestResultUtils";
import NodeUtils from "../../components/graph/NodeUtils";
import ProcessStateUtils from "../../components/Process/ProcessStateUtils";
import type { Scenario } from "../../components/Process/types";
import { isStatusRunning } from "../../components/Process/types";
import { ScenarioGraphSourceType } from "../../http/HttpService";
import type { ProcessCounts } from "../../http/resultsWithCountsDto";
import type { ScenarioGraph } from "../../types";
import type { TestData } from "../graph";
import type { RootState } from "../index";
import { getHistoryPast } from "./getHistory";
import { areLabelsUpdated, isGraphUpdated } from "./helpers";
import { getProcessState } from "./scenarioState";
import { getUserSettings } from "./userSettings";

export const getGraph = (state: RootState) => state.graphReducer.present;
export const getScenarioLoading = createSelector(getGraph, (g) => g.scenarioLoading);

export const getScenario = createSelector(getGraph, (g) => g.scenario);
export const getSavedScenario = createSelector(getHistoryPast, getScenario, (past, scenario): Scenario => past?.[0]?.scenario || scenario);

export const getScenarioGraph = createSelector(getGraph, (g) => g.scenario.scenarioGraph || ({} as ScenarioGraph), {
    memoizeOptions: { equalityCheck: isEqual, resultEqualityCheck: isEqual },
});

export const getNodes = createSelector(getScenarioGraph, (g) => g.nodes);

export const getScenarioLabels = createSelector(getGraph, (g) => g.scenario.labels);
export const getProcessNodesIds = createSelector(getScenarioGraph, (p) => NodeUtils.nodesFromScenarioGraph(p).map((n) => n.id));

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
export const isPristine = createSelector(getScenario, isProcessRenamed, getSavedScenario, (scenario, isProcessRenamed, savedScenario) => {
    if (isEmpty(scenario)) return true;
    if (isProcessRenamed) return false;
    if (areLabelsUpdated(scenario.labels, savedScenario.labels)) return false;
    if (isGraphUpdated(scenario.scenarioGraph, savedScenario.scenarioGraph)) return false;
    return true;
});

export const isValidationResultPresent = createSelector(getScenario, (p) => ProcessUtils.isValidationResultPresent(p));
export const hasError = createSelector(getScenario, (p) => !ProcessUtils.hasNoErrors(p));
export const hasWarnings = createSelector(getScenario, (p) => !ProcessUtils.hasNoWarnings(p));
export const hasPropertiesErrors = createSelector(getScenario, (p) => !ProcessUtils.hasNoPropertiesErrors(p));
export const getScenarioLabelsErrors = createSelector(getScenario, (p) => ProcessUtils.getLabelsErrors(p));
export const getSelectionState = createSelector(getGraph, (g) => g.selectionState);
export const getSelection = createSelector(getSelectionState, getScenarioGraph, (s, p) => NodeUtils.getAllNodesByIdWithEdges(s, p));
export const canModifySelectedNodes = createSelector(getSelectionState, (s) => !isEmpty(s));
export const isSaveDisabled = createSelector([isPristine, isLatestProcessVersion], (pristine, latest) => pristine && latest);
export const isDeployVisible = createSelector([getProcessState], (state) => ProcessStateUtils.canSeeDeploy(state));
export const isDeployPossible = createSelector(
    [isSaveDisabled, hasError, getProcessState, isFragment, getUserSettings],
    (saveDisabled, error, state, fragment, userSettings) => {
        const isAllowedByScenarioSave = userSettings["toolbar.autoSaveDuringDeployRedeploy"] || saveDisabled;
        return !fragment && isAllowedByScenarioSave && !error && ProcessStateUtils.canDeploy(state);
    },
);
export const isRedeployVisible = createSelector([getProcessState], (state) => ProcessStateUtils.canSeeRedeploy(state));
export const isRedeployPossible = createSelector(
    [isSaveDisabled, hasError, getProcessState, isFragment, getUserSettings],
    (saveDisabled, error, state, fragment, userSettings) => {
        const isAllowedByScenarioSave = userSettings["toolbar.autoSaveDuringDeployRedeploy"] || saveDisabled;
        return !fragment && isAllowedByScenarioSave && !error && ProcessStateUtils.canRedeploy(state);
    },
);
export const isCancelPossible = createSelector(getProcessState, (state) => ProcessStateUtils.canCancel(state));
export const isRunOffScheduleVisible = createSelector([getProcessState], (state) => ProcessStateUtils.canSeeRunOffSchedule(state));
export const isRunOffSchedulePossible = createSelector(
    [hasError, getProcessState, isFragment],
    (error, state, fragment) => !fragment && !error && ProcessStateUtils.canRunOffSchedule(state),
);
export const isMigrationPossible = createSelector(
    [isSaveDisabled, hasError, getProcessState, isFragment],
    (saveDisabled, error, state, fragment) => saveDisabled && !error && (fragment || ProcessStateUtils.canDeploy(state)),
);
export const isArchivePossible = createSelector(
    [getProcessState, isFragment],
    (state, isFragment) => isFragment || ProcessStateUtils.canArchive(state),
);
export const getTestCapabilities = createSelector(getGraph, (g) => g.testCapabilities);
export const getTestType = createSelector(getGraph, (g) => g.testType);
export const getPerformedTestType = createSelector(getGraph, (g) => g.performedTestType);
export const getTestParameters = createSelector(getGraph, (g) => g.testFormParameters || ([] as TestFormParameters[]));
export const getTestResults = createSelector(getGraph, (g) => g.testResults);
export const getTestResultsLoading = createSelector(getGraph, (g) => g.testResultsLoading);
export const getTestData = createSelector(getGraph, (g) => g.testData || ({} as TestData));
export const getProcessCountsRefresh = createSelector(getGraph, (g) => g.processCountsRefresh || null);
export const getProcessCounts = createSelector(getGraph, (g): ProcessCounts => g.processCounts || ({} as ProcessCounts));
export const getIsTestingMode = createSelector(
    getTestResults,
    getProcessCounts,
    (results, counts) => !isEmpty(results) || !isEmpty(counts),
);

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

export const getScenarioGraphSource = createSelector(
    [isSaveDisabled, getGraph, getScenarioLabels, getProcessVersionId],
    (isSaveDisabled, graph, labels, versionId) =>
        isSaveDisabled
            ? { type: ScenarioGraphSourceType.LATEST_VERSION }
            : {
                  type: ScenarioGraphSourceType.FROM_GRAPH,
                  scenarioGraph: graph?.scenario?.scenarioGraph,
                  scenarioLabels: labels,
                  baseScenarioVersionId: versionId,
              },
);
