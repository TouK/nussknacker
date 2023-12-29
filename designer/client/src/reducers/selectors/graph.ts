import { isEmpty } from "lodash";
import { createSelector } from "reselect";
import ProcessUtils from "../../common/ProcessUtils";
import NodeUtils from "../../components/graph/NodeUtils";
import ProcessStateUtils from "../../components/Process/ProcessStateUtils";
import { Process } from "../../types";
import { ProcessCounts } from "../graph";
import { RootState } from "../index";
import { getProcessState } from "./scenarioState";

export const getGraph = (state: RootState) => state.graphReducer.history.present;

export const getFetchedProcessDetails = createSelector(getGraph, (g) => g.fetchedProcessDetails);
export const getProcessToDisplay = createSelector(getGraph, (g) => g.processToDisplay || ({} as Process));
export const getProcessNodesIds = createSelector(getProcessToDisplay, (p) => NodeUtils.nodesFromProcess(p).map((n) => n.id));
export const getProcessId = createSelector(getFetchedProcessDetails, (d) => d?.name);
export const getProcessName = getProcessId;
export const getProcessUnsavedNewName = createSelector(getGraph, (g) => g?.unsavedNewName);
export const getProcessVersionId = createSelector(getFetchedProcessDetails, (d) => d?.processVersionId);
export const getProcessCategory = createSelector(getFetchedProcessDetails, (d) => d?.processCategory || "");
export const isLatestProcessVersion = createSelector(getFetchedProcessDetails, (d) => d?.isLatestVersion);
export const isFragment = createSelector(getProcessToDisplay, (p) => p.properties?.isFragment);
export const isArchived = createSelector(getFetchedProcessDetails, (p) => p?.isArchived);
export const isPristine = (state: RootState): boolean => ProcessUtils.nothingToSave(state) && !isProcessRenamed(state);
export const hasError = createSelector(getProcessToDisplay, (p) => !ProcessUtils.hasNoErrors(p));
export const hasWarnings = createSelector(getProcessToDisplay, (p) => !ProcessUtils.hasNoWarnings(p));
export const hasPropertiesErrors = createSelector(getProcessToDisplay, (p) => !ProcessUtils.hasNoPropertiesErrors(p));
export const getSelectionState = createSelector(getGraph, (g) => g.selectionState);
export const getSelection = createSelector(getSelectionState, getProcessToDisplay, (s, p) => NodeUtils.getAllNodesByIdWithEdges(s, p));
export const canModifySelectedNodes = createSelector(getSelectionState, (s) => !isEmpty(s));
export const getHistoryPast = (state: RootState) => state.graphReducer.history.past;
export const getHistoryFuture = (state: RootState) => state.graphReducer.history.future;

export const isProcessRenamed = createSelector(
    getProcessName,
    getProcessUnsavedNewName,
    (currentName, unsavedNewName) => unsavedNewName && unsavedNewName !== currentName,
);
export const getProcessToDisplayWithUnsavedName = createSelector(
    [getProcessToDisplay, getProcessUnsavedNewName, isProcessRenamed],
    (process, unsavedName, isProcessRenamed) => ({ ...process, id: isProcessRenamed ? unsavedName : process.id }),
);

export const isSaveDisabled = createSelector([isPristine, isLatestProcessVersion], (pristine, latest) => pristine && latest);
export const isDeployPossible = createSelector(
    [isSaveDisabled, hasError, getProcessState, isFragment],
    (saveDisabled, error, state, fragment) => !fragment && saveDisabled && !error && ProcessStateUtils.canDeploy(state),
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
export const getTestParameters = createSelector(getGraph, (g) => g.testFormParameters);
export const getTestResults = createSelector(getGraph, (g) => g.testResults);
export const getProcessCounts = createSelector(getGraph, (g): ProcessCounts => g.processCounts || ({} as ProcessCounts));
export const getShowRunProcessDetails = createSelector(
    [getTestResults, getProcessCounts],
    (testResults, processCounts) => testResults || processCounts,
);

export const getVersions = createSelector(getFetchedProcessDetails, (details) => details?.history || []);
export const hasOneVersion = createSelector(getVersions, (h) => h.length <= 1);
export const getAdditionalFields = createSelector(getProcessToDisplay, (p) => p.properties?.additionalFields);

export const getLayout = createSelector(getGraph, (state) => state.layout || []);
