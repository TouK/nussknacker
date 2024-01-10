import { isEmpty } from "lodash";
import { createSelector } from "reselect";
import ProcessUtils from "../../common/ScenarioUtils";
import NodeUtils from "../../components/graph/NodeUtils";
import ProcessStateUtils from "../../components/Process/ScenarioStateUtils";
import { ScenarioGraph } from "../../types";
import { ProcessCounts } from "../graph";
import { RootState } from "../index";
import { getScenarioState } from "./scenarioState";

export const getGraph = (state: RootState) => state.graphReducer.history.present;

export const getScenario = createSelector(getGraph, (g) => g.scenario);
export const getScenarioGraph = createSelector(getGraph, (g) => g.scenario.json || ({} as ScenarioGraph));
export const getScenarioNodesIds = createSelector(getScenarioGraph, (p) => NodeUtils.nodesFromScenarioGraph(p).map((n) => n.id));
export const getScenarioName = createSelector(getScenario, (d) => d?.name);
export const getScenarioUnsavedNewName = createSelector(getGraph, (g) => g?.unsavedNewName);
export const getScenarioVersionId = createSelector(getScenario, (d) => d?.processVersionId);
export const getScenarioCategory = createSelector(getScenario, (d) => d?.processCategory || "");
export const isLatestScenarioVersion = createSelector(getScenario, (d) => d?.isLatestVersion);
export const isFragment = createSelector(getScenarioGraph, (p) => p.properties?.isFragment);
export const isArchived = createSelector(getScenario, (p) => p?.isArchived);
export const isPristine = (state: RootState): boolean => ProcessUtils.nothingToSave(state) && !isScenarioRenamed(state);
export const hasError = createSelector(getScenario, (p) => !ProcessUtils.hasNoErrors(p));
export const hasWarnings = createSelector(getScenario, (p) => !ProcessUtils.hasNoWarnings(p));
export const hasPropertiesErrors = createSelector(getScenario, (p) => !ProcessUtils.hasNoPropertiesErrors(p));
export const getSelectionState = createSelector(getGraph, (g) => g.selectionState);
export const getSelection = createSelector(getSelectionState, getScenarioGraph, (s, p) => NodeUtils.getAllNodesByIdWithEdges(s, p));
export const canModifySelectedNodes = createSelector(getSelectionState, (s) => !isEmpty(s));
export const getHistoryPast = (state: RootState) => state.graphReducer.history.past;
export const getHistoryFuture = (state: RootState) => state.graphReducer.history.future;

export const isScenarioRenamed = createSelector(
    getScenarioName,
    getScenarioUnsavedNewName,
    (currentName, unsavedNewName) => unsavedNewName && unsavedNewName !== currentName,
);
export const getScenarioWithUnsavedName = createSelector(
    [getScenario, getScenarioUnsavedNewName, isScenarioRenamed],
    (process, unsavedName, isProcessRenamed) => ({ ...process, name: isProcessRenamed ? unsavedName : process.name }),
);

export const isSaveDisabled = createSelector([isPristine, isLatestScenarioVersion], (pristine, latest) => pristine && latest);
export const isDeployPossible = createSelector(
    [isSaveDisabled, hasError, getScenarioState, isFragment],
    (saveDisabled, error, state, fragment) => !fragment && saveDisabled && !error && ProcessStateUtils.canDeploy(state),
);
export const isMigrationPossible = createSelector(
    [isSaveDisabled, hasError, getScenarioState, isFragment],
    (saveDisabled, error, state, fragment) => saveDisabled && !error && (fragment || ProcessStateUtils.canDeploy(state)),
);
export const isCancelPossible = createSelector(getScenarioState, (state) => ProcessStateUtils.canCancel(state));
export const isArchivePossible = createSelector(
    [getScenarioState, isFragment],
    (state, isFragment) => isFragment || ProcessStateUtils.canArchive(state),
);
export const getTestCapabilities = createSelector(getGraph, (g) => g.testCapabilities);
export const getTestParameters = createSelector(getGraph, (g) => g.testFormParameters);
export const getTestResults = createSelector(getGraph, (g) => g.testResults);
export const getProcessCounts = createSelector(getGraph, (g): ProcessCounts => g.processCounts || ({} as ProcessCounts));
export const getShowRunProcessDetails = createSelector(
    [getTestResults, getProcessCounts],
    (testResults, processCounts) => testResults || processCounts,
);

export const getVersions = createSelector(getScenario, (details) => details?.history || []);
export const hasOneVersion = createSelector(getVersions, (h) => h.length <= 1);
export const getAdditionalFields = createSelector(getScenarioGraph, (p) => p.properties?.additionalFields);

export const getLayout = createSelector(getGraph, (state) => state.layout || []);
