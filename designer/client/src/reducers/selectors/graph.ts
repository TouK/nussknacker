import { isEmpty, isEqual, omit } from "lodash";
import { createSelector } from "reselect";

import ProcessUtils from "../../common/ProcessUtils";
import type { TestFormParameters } from "../../common/TestResultUtils";
import NodeUtils from "../../components/graph/NodeUtils";
import type { ScenarioLabelValidationError } from "../../components/Labels/types";
import ProcessStateUtils from "../../components/Process/ProcessStateUtils";
import type { Scenario } from "../../components/Process/types";
import type { ScenarioGraph } from "../../types";
import type { ProcessCounts, TestData } from "../graph";
import type { RootState } from "../index";
import { getHistoryPast } from "./getHistory";
import { getProcessState } from "./scenarioState";

export const getGraph = (state: RootState) => state.graphReducer.present;

export const getScenario = createSelector(getGraph, (g) => g.scenario);
export const getScenarioGraph = createSelector(getGraph, (g) => g.scenario.scenarioGraph || ({} as ScenarioGraph), {
    memoizeOptions: {
        equalityCheck: isEqual,
        resultEqualityCheck: isEqual,
    },
});

export const getNodes = createSelector(getScenarioGraph, (g) => g.nodes);

export const getScenarioLabels = createSelector(getGraph, (g) => g.scenario.labels);
export const getProcessNodesIds = createSelector(getScenarioGraph, (p) => NodeUtils.nodesFromScenarioGraph(p).map((n) => n.id));
export const getProcessName = createSelector(getScenario, (d) => d?.name);
export const getProcessUnsavedNewName = createSelector(getScenarioGraph, (g) => g.properties?.name);
export const getProcessVersionId = createSelector(getScenario, (d) => d?.processVersionId);
export const getProcessCategory = createSelector(getScenario, (d) => d?.processCategory || "");
export const getProcessingType = createSelector(getScenario, (d) => d?.processingType);
export const isLatestProcessVersion = createSelector(getScenario, (d) => d?.isLatestVersion);
export const isFragment = createSelector(getScenario, (p) => p?.isFragment);
export const isArchived = createSelector(getScenario, (p) => p?.isArchived);

export const nothingToSave = createSelector(
    (state: RootState) => state,
    getScenario,
    getHistoryPast,
    (state, scenario, _getHistoryPast) => {
        const savedProcessState: Scenario = _getHistoryPast?.[0]?.scenario || scenario;

        /**
         * It's a fix of https://touk-jira.atlassian.net/browse/NU-2194
         * When node is added from a toolbar, branchParametersTemplate are initially added to the node, but when we perform a scenario save, node has no branchParametersTemplate
         * Let's ignore branchParametersTemplate in a button save state checking
         */
        const omitBranchParametersTemplate = (details: ScenarioGraph) => {
            if (!details.nodes?.length) {
                return details;
            }

            return {
                ...details,
                nodes: details.nodes.map((node) => omit(node, ["branchParametersTemplate"])),
            };
        };
        const processRenamed = isProcessRenamed(state);

        if (processRenamed) {
            return false;
        }

        if (isEmpty(scenario)) {
            return true;
        }

        const labelsFor = (scenario: Scenario): string[] => {
            return scenario.labels ? scenario.labels.slice().sort((a, b) => a.localeCompare(b)) : [];
        };

        const isGraphUpdated = isEqual(
            omitBranchParametersTemplate(scenario.scenarioGraph),
            omitBranchParametersTemplate(savedProcessState.scenarioGraph),
        );
        const areScenarioLabelsUpdated = isEqual(labelsFor(scenario), labelsFor(savedProcessState));

        return !savedProcessState || (isGraphUpdated && areScenarioLabelsUpdated);
    },
);
export const canExport = createSelector(getScenario, (scenario) => (isEmpty(scenario) ? false : !isEmpty(scenario.scenarioGraph.nodes)));
export const isValidationResultPresent = createSelector(getGraph, (validationResult) => Boolean(validationResult));
export const getValidationResult = createSelector(getScenario, (scenario) => ProcessUtils.getValidationResult(scenario));
export const hasNoWarnings = createSelector(getScenario, getValidationResult, (scenario, _getValidationResult) => {
    const warnings = _getValidationResult.warnings;
    return isEmpty(warnings) || Object.keys(warnings.invalidNodes || {}).length == 0;
});
export const getLabelsErrors = createSelector(getValidationResult, (_getValidationResult): ScenarioLabelValidationError[] => {
    return _getValidationResult.errors.globalErrors
        .filter((e) => e.error.typ == "ScenarioLabelValidationError")
        .map(
            (e) =>
                <ScenarioLabelValidationError>{
                    label: e.error.fieldName,
                    messages: [e.error.description],
                },
        );
});
export const getValidationErrors = createSelector(getScenario, (scenario) => ProcessUtils.getValidationErrors(scenario));
export const hasNoErrors = createSelector(getScenario, getValidationErrors, (scenario, _getValidationErrors) => {
    const result = _getValidationErrors;
    return (
        !result ||
        (Object.keys(result.invalidNodes || {}).length == 0 &&
            (result.globalErrors || []).length == 0 &&
            (result.processPropertiesErrors || []).length == 0)
    );
});
export const getNodeResults = createSelector(getValidationResult, (results) => results.nodeResults);
export const hasNeitherErrorsNorWarnings = createSelector(
    isValidationResultPresent,
    hasNoWarnings,
    hasNoErrors,
    (_isValidationResultPresent, _hasNoWarnings, _hasNoErrors) => {
        //fixme maybe return hasErrors flag from backend?
        return _isValidationResultPresent && _hasNoErrors && _hasNoWarnings;
    },
);
export const hasError = createSelector(hasNoErrors, (p) => !p);
export const hasWarnings = createSelector(hasNoWarnings, (p) => !p);
export const hasPropertiesErrors = createSelector(
    getValidationErrors,
    (_getValidationErrors) => !isEmpty(_getValidationErrors?.processPropertiesErrors),
);
export const getSelectionState = createSelector(getGraph, (g) => g.selectionState);
export const getSelection = createSelector(getSelectionState, getScenarioGraph, (s, p) => NodeUtils.getAllNodesByIdWithEdges(s, p));
export const canModifySelectedNodes = createSelector(getSelectionState, (s) => !isEmpty(s));

export const getUnsavedOrCurrentName = createSelector(getProcessName, getProcessUnsavedNewName, (currentName, unsavedNewName) => {
    return unsavedNewName || currentName;
});

export const isProcessRenamed = createSelector(
    getProcessName,
    getUnsavedOrCurrentName,
    (currentName, unsavedNewName) => unsavedNewName !== currentName,
);
export const isPristine = createSelector(
    nothingToSave,
    isProcessRenamed,
    (_nothingToSave, _isProcessRenamed) => _nothingToSave && !_isProcessRenamed,
);

export const isSaveDisabled = createSelector([isPristine, isLatestProcessVersion], (pristine, latest) => pristine && latest);
export const isDeployVisible = createSelector([getProcessState], (state) => ProcessStateUtils.canSeeDeploy(state));
export const isDeployPossible = createSelector(
    [isSaveDisabled, hasError, getProcessState, isFragment],
    (saveDisabled, error, state, fragment) => !fragment && saveDisabled && !error && ProcessStateUtils.canDeploy(state),
);
export const isRedeployVisible = createSelector([getProcessState], (state) => ProcessStateUtils.canSeeRedeploy(state));
export const isRedeployPossible = createSelector(
    [isSaveDisabled, hasError, getProcessState, isFragment],
    (saveDisabled, error, state, fragment) => !fragment && saveDisabled && !error && ProcessStateUtils.canRedeploy(state),
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

export const getShowRunProcessDetails = createSelector(
    [getTestResults, getProcessCounts],
    (testResults, processCounts) => testResults || processCounts,
);

export const getVersions = createSelector(getScenario, (details) => details?.history || []);
export const hasOneVersion = createSelector(getVersions, (h) => h.length <= 1);
export const getProperties = createSelector(getProcessName, getScenarioGraph, (name, graph) => {
    return { name, ...graph.properties };
});
export const getAdditionalFields = createSelector(getProperties, (p) => p?.additionalFields);
export const getScenarioDescription = createSelector(getAdditionalFields, (f): [string, boolean] => [f?.description, f?.showDescription]);

export const getLayout = createSelector(getGraph, (state) => state.layout || []);
