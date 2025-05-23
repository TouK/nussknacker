import { isEmpty, isEqual, omit } from "lodash";

import type { ScenarioLabelValidationError } from "../components/Labels/types";
import type { Scenario } from "../components/Process/types";
import type { RootState } from "../reducers";
import { getHistoryPast } from "../reducers/selectors/getHistory";
import { getScenario, isProcessRenamed } from "../reducers/selectors/graph";
import type { NodeResults, ScenarioGraph, ValidationErrors, ValidationResult } from "../types";

// easy extractable selectors-like
const nothingToSave = (state: RootState): boolean => {
    const scenario: Scenario = getScenario(state);
    const savedProcessState: Scenario = getHistoryPast(state)?.[0]?.scenario || scenario;

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
};

const canExport = (state: RootState): boolean => {
    const scenario = getScenario(state);
    return isEmpty(scenario) ? false : !isEmpty(scenario.scenarioGraph.nodes);
};

const isValidationResultPresent = (scenario: Scenario) => {
    return Boolean(scenario.validationResult);
};

const getValidationResult = (scenario: Scenario): ValidationResult =>
    scenario?.validationResult || {
        validationErrors: [],
        validationWarnings: [],
        nodeResults: {},
        errors: {
            globalErrors: [],
            processPropertiesErrors: [],
            invalidNodes: {},
        },
    };

const getValidationErrors = (scenario: Scenario): ValidationErrors => getValidationResult(scenario).errors;

const getNodeResults = (scenario: Scenario): NodeResults => getValidationResult(scenario).nodeResults;

const getLabelsErrors = (scenario: Scenario): ScenarioLabelValidationError[] => {
    return getValidationResult(scenario)
        .errors.globalErrors.filter((e) => e.error.typ == "ScenarioLabelValidationError")
        .map(
            (e) =>
                <ScenarioLabelValidationError>{
                    label: e.error.fieldName,
                    messages: [e.error.description],
                },
        );
};

const hasNoPropertiesErrors = (scenario: Scenario) => {
    return isEmpty(getValidationErrors(scenario)?.processPropertiesErrors);
};

const hasNoWarnings = (scenario: Scenario) => {
    const warnings = getValidationResult(scenario).warnings;
    return isEmpty(warnings) || Object.keys(warnings.invalidNodes || {}).length == 0;
};

const hasNoErrors = (scenario: Scenario) => {
    const result = getValidationErrors(scenario);
    return (
        !result ||
        (Object.keys(result.invalidNodes || {}).length == 0 &&
            (result.globalErrors || []).length == 0 &&
            (result.processPropertiesErrors || []).length == 0)
    );
};

const hasNeitherErrorsNorWarnings = (scenario: Scenario) => {
    //fixme maybe return hasErrors flag from backend?
    return isValidationResultPresent(scenario) && hasNoErrors(scenario) && hasNoWarnings(scenario);
};

export default {
    nothingToSave,
    canExport,
    isValidationResultPresent,
    getValidationResult,
    hasNeitherErrorsNorWarnings,
    hasNoErrors,
    hasNoWarnings,
    hasNoPropertiesErrors,
    getLabelsErrors,
    getNodeResults,
    getValidationErrors,
};
