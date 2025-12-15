import type { AxiosResponse } from "axios";

import type { Scenario } from "../../components/Process/types";
import HttpService from "../../http/HttpService/instance";
import { getUserSettings } from "../../reducers/selectors/userSettings";
import type { ScenarioGraph } from "../../types/scenarioGraph";
import type { ValidationResult } from "../../types/validation";
import type { ThunkAction } from "../reduxTypes";

export function preApplyValidation(
    scenarioBefore: Scenario,
    scenarioGraph: ScenarioGraph,
    controller: AbortController,
): ThunkAction<Promise<AxiosResponse<ValidationResult>>> {
    return (dispatch, getState) => {
        const userSettings = getUserSettings(getState());
        if (userSettings["node.autoApply"]) return;

        return HttpService.validateProcess(scenarioBefore.name, scenarioGraph.properties.name, scenarioGraph, controller);
    };
}
