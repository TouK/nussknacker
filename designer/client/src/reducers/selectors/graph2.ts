import { createSelector } from "reselect";

import ProcessUtils from "../../common/ProcessUtils";
import ProcessStateUtils from "../../components/Process/ProcessStateUtils";
import { getScenario, isFragment, isSaveDisabled } from "./graph";
import { getProcessState } from "./scenarioState";
import { getUserSettings } from "./userSettings";

export const getScenarioLabelsErrors = createSelector(getScenario, (p) => ProcessUtils.getLabelsErrors(p));
export const hasError = createSelector(getScenario, (p) => !ProcessUtils.hasNoErrors(p));
export const hasPropertiesErrors = createSelector(getScenario, (p) => !ProcessUtils.hasNoPropertiesErrors(p));
export const hasWarnings = createSelector(getScenario, (p) => !ProcessUtils.hasNoWarnings(p));
export const isDeployPossible = createSelector(
    [isSaveDisabled, hasError, getProcessState, isFragment, getUserSettings],
    (saveDisabled, error, state, fragment, userSettings) => {
        const isAllowedByScenarioSave = userSettings["toolbar.autoSaveDuringDeployRedeploy"] || saveDisabled;
        return !fragment && isAllowedByScenarioSave && !error && ProcessStateUtils.canDeploy(state);
    },
);
export const isMigrationPossible = createSelector(
    [isSaveDisabled, hasError, getProcessState, isFragment],
    (saveDisabled, error, state, fragment) =>
        saveDisabled && !error && (fragment || ProcessStateUtils.canDeploy(state) || ProcessStateUtils.canRedeploy(state)),
);
export const isRedeployPossible = createSelector(
    [isSaveDisabled, hasError, getProcessState, isFragment, getUserSettings],
    (saveDisabled, error, state, fragment, userSettings) => {
        const isAllowedByScenarioSave = userSettings["toolbar.autoSaveDuringDeployRedeploy"] || saveDisabled;
        return !fragment && isAllowedByScenarioSave && !error && ProcessStateUtils.canRedeploy(state);
    },
);
export const isRunOffSchedulePossible = createSelector(
    [hasError, getProcessState, isFragment],
    (error, state, fragment) => !fragment && !error && ProcessStateUtils.canRunOffSchedule(state),
);
export const isValidationResultPresent = createSelector(getScenario, (p) => ProcessUtils.isValidationResultPresent(p));
