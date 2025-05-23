import { isEmpty } from "lodash";
import { createSelector } from "reselect";

import type { Scenario } from "../components/Process/types";
import type { RootState } from "../reducers";
import { getScenario } from "../reducers/selectors/graph";
import {
    getLabelsErrors as _getLabelsErrors,
    getNodeResults as _getNodeResults,
    getValidationErrors as _getValidationErrors,
    getValidationResult as _getValidationResult,
    hasNoErrors as _hasNoErrors,
    hasNoPropertiesErrors as _hasNoPropertiesErrors,
    hasNoWarnings as _hasNoWarnings,
    isValidationResultPresent as _isValidationResultPresent,
    nothingToSave as _nothingToSave,
} from "./ProcessUtils2";

export const nothingToSave = createSelector(
    (state: RootState) => state,
    (state) => _nothingToSave(state),
);
export const canExport = createSelector(getScenario, (scenario) => (isEmpty(scenario) ? false : !isEmpty(scenario.scenarioGraph.nodes)));
export const isValidationResultPresent = createSelector(getScenario, (scenario) => _isValidationResultPresent(scenario));
export const hasNeitherErrorsNorWarnings = createSelector(getScenario, (scenario: Scenario) => {
    //fixme maybe return hasErrors flag from backend?
    return _isValidationResultPresent(scenario) && _hasNoErrors(scenario) && _hasNoWarnings(scenario);
});
export const hasNoErrors = createSelector(getScenario, (scenario) => _hasNoErrors(scenario));
export const getValidationResult = createSelector(getScenario, (scenario) => _getValidationResult(scenario));
export const hasNoWarnings = createSelector(getScenario, (scenario) => _hasNoWarnings(scenario));
export const hasNoPropertiesErrors = createSelector(getScenario, (scenario) => _hasNoPropertiesErrors(scenario));
export const getLabelsErrors = createSelector(getScenario, (scenario) => _getLabelsErrors(scenario));
export const getValidationErrors = createSelector(getScenario, (scenario) => _getValidationErrors(scenario));
export const getNodeResults = createSelector(getScenario, (scenario) => _getNodeResults(scenario));
