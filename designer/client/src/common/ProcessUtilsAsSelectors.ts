import { isEmpty, isEqual, omit } from "lodash";
import { createSelector } from "reselect";

import type { ScenarioLabelValidationError } from "../components/Labels/types";
import type { Scenario } from "../components/Process/types";
import type { RootState } from "../reducers";
import { getHistoryPast } from "../reducers/selectors/getHistory";
import { getScenario, isProcessRenamed } from "../reducers/selectors/graph";
import type { ScenarioGraph } from "../types";
import { getValidationErrors as _getValidationErrors, getValidationResult as _getValidationResult } from "./ProcessUtils2";

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
export const isValidationResultPresent = createSelector(getScenario, (scenario: Scenario) => {
    return Boolean(scenario.validationResult);
});

export const getValidationResult = createSelector(getScenario, (scenario) => _getValidationResult(scenario));
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
export const getValidationErrors = createSelector(getScenario, (scenario) => _getValidationErrors(scenario));
export const hasNoErrors = createSelector(getScenario, getValidationErrors, (scenario, _getValidationErrors) => {
    const result = _getValidationErrors;
    return (
        !result ||
        (Object.keys(result.invalidNodes || {}).length == 0 &&
            (result.globalErrors || []).length == 0 &&
            (result.processPropertiesErrors || []).length == 0)
    );
});
export const hasNoPropertiesErrors = createSelector(getValidationErrors, (_getValidationErrors) => {
    return isEmpty(_getValidationErrors?.processPropertiesErrors);
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
