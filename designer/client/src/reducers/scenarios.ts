import { createSelector } from "reselect";

import type { Action, Reducer, ThunkAction } from "../actions/reduxTypes";
import type { Scenario } from "../components/Process/types";
import HttpService from "../http/HttpService/instance";
import type { RootState } from "./index";

export type ScenariosType = Scenario[];

export const reducer: Reducer<ScenariosType> = (state = [], action: Action) => {
    switch (action.type) {
        case "SCENARIOS_FETCHED":
            return action.data;
        default:
            return state;
    }
};

export type ScenariosActions =
    | {
          type: "GET_SCENARIOS";
      }
    | {
          type: "SCENARIOS_FETCHED";
          data: Scenario[];
      };

export function fetchScenarios(): ThunkAction {
    return async (dispatch) => {
        dispatch({ type: "GET_SCENARIOS" });

        const { data } = await HttpService.fetchProcesses();

        dispatch({ type: "SCENARIOS_FETCHED", data });

        return data;
    };
}

const getScenarios = (state: RootState) => state.scenarios;
export const getScenariosNames = createSelector(getScenarios, (scenarios) => scenarios.map((scenario) => scenario.name));
export const getActiveScenarios = createSelector(getScenarios, (scenarios) =>
    scenarios
        .map(({ history, isArchived, isLatestVersion, validationResult, processVersionId, ...scenario }) => (isArchived ? null : scenario))
        .filter(Boolean),
);
export const getActiveScenariosNames = createSelector(getActiveScenarios, (scenarios) => scenarios.map((scenario) => scenario.name));
