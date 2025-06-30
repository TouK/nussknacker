import { omit } from "lodash/fp";
import { ActionCreators as UndoActionCreators } from "redux-undo";

import type { ProcessName, ProcessStateType, ProcessVersionId, Scenario } from "../../components/Process/types";
import { replaceSearchQuery } from "../../containers/hooks/useSearchQuery";
import { getProcessDefinitionData } from "../../reducers/selectors/processDefinitionData";
import type { ProcessDefinitionData, ScenarioGraph } from "../../types";
import type { ThunkAction } from "../reduxTypes";
import HttpService from "./../../http/HttpService";
import { Initiator, stopLiveData } from "./liveData";

export type ScenarioActions =
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
            dispatch(displayTestCapabilities(processName, response.data.scenarioGraph));
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

export function displayTestCapabilities(processName: ProcessName, scenarioGraph: ScenarioGraph): ThunkAction {
    return (dispatch) =>
        HttpService.getTestCapabilities(processName, scenarioGraph).then(({ data }) =>
            dispatch({
                type: "UPDATE_TEST_CAPABILITIES",
                capabilities: data,
            }),
        );
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
        dispatch(UndoActionCreators.clearHistory());
        dispatch({ type: "CLEAR_PROCESS" });
    };
}

export function hideRunProcessDetails(): ThunkAction {
    replaceSearchQuery(omit(["from", "to", "refresh"]));
    return (dispatch, getState) => {
        dispatch(stopLiveData(Initiator.button));
        dispatch({ type: "HIDE_RUN_PROCESS_DETAILS" });
    };
}
