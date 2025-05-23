import { isEmpty } from "lodash";
import { createSelector } from "reselect";

import type { ScenarioLabelValidationError } from "../components/Labels/types";
import type { RootState } from "../reducers";
import { getScenario } from "../reducers/selectors/graph";
import {
    getValidationErrors as _getValidationErrors,
    getValidationResult as _getValidationResult,
    hasNoErrors as _hasNoErrors,
    isValidationResultPresent as _isValidationResultPresent,
    nothingToSave as _nothingToSave,
} from "./ProcessUtils2";

export const nothingToSave = createSelector(
    (state: RootState) => state,
    (state) => _nothingToSave(state),
);
export const canExport = createSelector(getScenario, (scenario) => (isEmpty(scenario) ? false : !isEmpty(scenario.scenarioGraph.nodes)));
export const isValidationResultPresent = createSelector(getScenario, (scenario) => _isValidationResultPresent(scenario));
export const hasNoErrors = createSelector(getScenario, (scenario) => _hasNoErrors(scenario));
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
