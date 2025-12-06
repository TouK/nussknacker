import { omit } from "lodash/fp";
import { ActionCreators as UndoActionCreators } from "redux-undo";

import type { TestCapabilities } from "../../common/TestResultUtils";
import type { PredefinedActionName, ProcessName, ProcessStateType, ProcessVersionId, Scenario } from "../../components/Process/types";
import { replaceSearchQuery } from "../../containers/hooks/useSearchQuery";
import { memoizeByArgsWithTTL } from "../../helpers/memoizeByArgsWithTTL";
import HttpService from "../../http/HttpService/instance";
import { getProcessDefinitionData } from "../../reducers/selectors/getProcessDefinitionData";
import type { ProcessDefinitionData, ScenarioGraph } from "../../types/scenarioGraph";
import type { Action, ThunkAction } from "../reduxTypes";
import { stopLiveData } from "./liveData";

export type ScenarioActions =
    | { type: "PENDING_SCENARIO_ACTION"; action: PredefinedActionName }
    | {
          type: "PROCESS_STATE_LOADED";
          processState: ProcessStateType;
      }
    | {
          type: "CORRECT_INVALID_SCENARIO";
          processDefinitionData: ProcessDefinitionData;
      }
    | {
          type: "DISPLAY_PROCESS";
          scenario: Scenario;
      }
    | {
          type: "UPDATE_IMPORTED_PROCESS";
          scenario: Scenario;
      }
    | { type: "CLEAR_PROCESS" }
    | { type: "HIDE_RUN_PROCESS_DETAILS" };

export function fetchProcessToDisplay(processName: ProcessName, versionId?: ProcessVersionId): ThunkAction<Promise<Scenario>> {
    return (dispatch) => {
        dispatch({ type: "PROCESS_FETCH" });

        return HttpService.fetchProcessDetails(processName, versionId).then((response) => {
            dispatch({
                type: "DISPLAY_PROCESS",
                scenario: response.data,
            });
            return response.data;
        });
    };
}

export function loadProcessState(processName: ProcessName, processVersionId: number): ThunkAction {
    return (dispatch) =>
        HttpService.fetchProcessState(processName, processVersionId).then(({ data }) =>
            dispatch({
                type: "PROCESS_STATE_LOADED",
                processState: data,
            }),
        );
}

const getTestCapabilities = memoizeByArgsWithTTL((processName: ProcessName, scenarioGraph: ScenarioGraph) => {
    return HttpService.getTestCapabilities(processName, scenarioGraph);
}, 1000);

export type UpdateTestCapabilitiesAction = { type: "UPDATE_TEST_CAPABILITIES"; capabilities: TestCapabilities };

export function displayTestCapabilities(processName: ProcessName, scenarioGraph: ScenarioGraph): ThunkAction {
    return (dispatch) => {
        if (getTestCapabilities.isCached(processName, scenarioGraph)) return;
        getTestCapabilities(processName, scenarioGraph).then(({ data }) => {
            return dispatch({
                type: "UPDATE_TEST_CAPABILITIES",
                capabilities: data,
            });
        });
    };
}

export function displayCurrentProcessVersion(processName: ProcessName): ThunkAction {
    return fetchProcessToDisplay(processName);
}

export function displayScenarioVersion(processName: ProcessName, versionId: ProcessVersionId): ThunkAction {
    return async (dispatch, getState) => {
        await dispatch(fetchProcessToDisplay(processName, versionId));
        const processDefinitionData = getProcessDefinitionData(getState());
        dispatch({ type: "CORRECT_INVALID_SCENARIO", processDefinitionData });
    };
}

export function clearProcess(): ThunkAction {
    return (dispatch) => {
        dispatch(UndoActionCreators.clearHistory() as Action);
        dispatch({ type: "CLEAR_PROCESS" });
    };
}

export function hideTestRunDetails(): ThunkAction {
    return (dispatch, getState) => {
        replaceSearchQuery(omit(["from", "to", "refresh"]));
        dispatch({ type: "HIDE_RUN_PROCESS_DETAILS" });
    };
}
export function hideRunProcessDetails(): ThunkAction {
    return (dispatch, getState) => {
        dispatch(stopLiveData());
        dispatch(hideTestRunDetails());
    };
}
