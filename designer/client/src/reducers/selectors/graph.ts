import { isEmpty, isEqual } from "lodash";
import { createSelector } from "reselect";
import ProcessUtils from "../../common/ProcessUtils";
import NodeUtils from "../../components/graph/NodeUtils";
import ProcessStateUtils from "../../components/Process/ProcessStateUtils";
import { ScenarioGraph } from "../../types";
import { ProcessCounts } from "../graph";
import { RootState } from "../index";
import { getProcessState } from "./scenarioState";
import { TestFormParameters } from "../../common/TestResultUtils";

export const getGraph = (state: RootState) => state.graphReducer.history.present;

export const getScenario = createSelector(getGraph, (g) => g.scenario);
export const getScenarioGraph = createSelector(getGraph, (g) => g.scenario.scenarioGraph || ({} as ScenarioGraph), {
    memoizeOptions: { equalityCheck: isEqual, resultEqualityCheck: isEqual },
});

export const getScenarioLabels = createSelector(getGraph, (g) => g.scenario.labels);
export const getProcessNodesIds = createSelector(getScenarioGraph, (p) => NodeUtils.nodesFromScenarioGraph(p).map((n) => n.id));
export const getProcessName = createSelector(getScenario, (d) => d?.name);
export const getProcessUnsavedNewName = createSelector(getGraph, (g) => g?.unsavedNewName);
export const getProcessVersionId = createSelector(getScenario, (d) => d?.processVersionId);
export const getProcessCategory = createSelector(getScenario, (d) => d?.processCategory || "");
export const getProcessingType = createSelector(getScenario, (d) => d?.processingType);
export const isLatestProcessVersion = createSelector(getScenario, (d) => d?.isLatestVersion);
export const isFragment = createSelector(getScenario, (p) => p?.isFragment);
export const isArchived = createSelector(getScenario, (p) => p?.isArchived);
export const isPristine = (state: RootState): boolean => ProcessUtils.nothingToSave(state) && !isProcessRenamed(state);
export const hasError = createSelector(getScenario, (p) => !ProcessUtils.hasNoErrors(p));
export const hasWarnings = createSelector(getScenario, (p) => !ProcessUtils.hasNoWarnings(p));
export const hasPropertiesErrors = createSelector(getScenario, (p) => !ProcessUtils.hasNoPropertiesErrors(p));
export const getScenarioLabelsErrors = createSelector(getScenario, (p) => ProcessUtils.getLabelsErrors(p));
export const getSelectionState = createSelector(getGraph, (g) => g.selectionState);
export const getSelection = createSelector(getSelectionState, getScenarioGraph, (s, p) => NodeUtils.getAllNodesByIdWithEdges(s, p));
export const canModifySelectedNodes = createSelector(getSelectionState, (s) => !isEmpty(s));
export const getHistoryPast = (state: RootState) => state.graphReducer.history.past;
export const getHistoryFuture = (state: RootState) => state.graphReducer.history.future;

export const isProcessRenamed = createSelector(
    getProcessName,
    getProcessUnsavedNewName,
    (currentName, unsavedNewName) => unsavedNewName && unsavedNewName !== currentName,
);
export const getUnsavedOrCurrentName = createSelector(
    getProcessName,
    getProcessUnsavedNewName,
    (currentName, unsavedNewName) => (unsavedNewName && unsavedNewName !== currentName && unsavedNewName) || currentName,
);
export const isSaveDisabled = createSelector([isPristine, isLatestProcessVersion], (pristine, latest) => pristine && latest);
export const isDeployPossible = createSelector(
    [isSaveDisabled, hasError, getProcessState, isFragment],
    (saveDisabled, error, state, fragment) => !fragment && saveDisabled && !error && ProcessStateUtils.canDeploy(state),
);
export const isDeployedVersion = createSelector(
    [getProcessVersionId, createSelector(getScenario, (scenario) => scenario?.lastDeployedAction?.processVersionId)],
    (visibleVersion, deployedVersion) => visibleVersion === deployedVersion,
);
export const isMigrationPossible = createSelector(
    [isSaveDisabled, hasError, getProcessState, isFragment],
    (saveDisabled, error, state, fragment) => saveDisabled && !error && (fragment || ProcessStateUtils.canDeploy(state)),
);
export const isCancelPossible = createSelector(getProcessState, (state) => ProcessStateUtils.canCancel(state));
export const isArchivePossible = createSelector(
    [getProcessState, isFragment],
    (state, isFragment) => isFragment || ProcessStateUtils.canArchive(state),
);
export const getTestCapabilities = createSelector(getGraph, (g) => g.testCapabilities);
export const getTestParameters = createSelector(getGraph, (g) => g.testFormParameters || ([] as TestFormParameters[]));
export const getTestResults = createSelector(getGraph, (g) => g.testResults);
export const getProcessCountsRefresh = createSelector(getGraph, (g) => g.processCountsRefresh || null);
export const getProcessCounts = createSelector(getGraph, (g): ProcessCounts => g.processCounts || ({} as ProcessCounts));
export const getShowRunProcessDetails = createSelector(
    [getTestResults, getProcessCounts],
    (testResults, processCounts) => testResults || processCounts,
);

export const getVersions = createSelector(getScenario, (details) => details?.history || []);
export const hasOneVersion = createSelector(getVersions, (h) => h.length <= 1);
export const getAdditionalFields = createSelector(getScenarioGraph, (p) => p.properties?.additionalFields);
export const getScenarioDescription = createSelector(getAdditionalFields, (f): [string, boolean] => [f?.description, f?.showDescription]);

export const getLayout = createSelector(getGraph, (state) => state.layout || []);

export const getActivities = createSelector(
    (state: RootState) => state.processActivity,
    (state) => state.activities || [],
);
