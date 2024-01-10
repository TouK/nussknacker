import { ThunkAction } from "../reduxTypes";
import { ScenarioGraph, ProcessDefinitionData } from "../../types";
import HttpService from "./../../http/HttpService";
import { Scenario, ProcessName, ProcessVersionId } from "../../components/Process/types";
import { displayProcessActivity } from "./displayProcessActivity";
import { ActionCreators as UndoActionCreators } from "redux-undo";
import { getProcessDefinitionData } from "../../reducers/selectors/settings";

export type ScenarioActions =
    | { type: "CORRECT_INVALID_SCENARIO"; processDefinitionData: ProcessDefinitionData }
    | { type: "DISPLAY_PROCESS"; fetchedProcessDetails: Scenario };

export function fetchScenario(processName: ProcessName, versionId?: ProcessVersionId): ThunkAction<Promise<Scenario>> {
    return (dispatch) => {
        dispatch({ type: "PROCESS_FETCH" });

        return HttpService.fetchProcessDetails(processName, versionId).then((response) => {
            dispatch(displayTestCapabilities(response.data.json));
            dispatch({
                type: "DISPLAY_PROCESS",
                fetchedProcessDetails: response.data,
            });
            return response.data;
        });
    };
}

export function loadProcessState(processName: ProcessName): ThunkAction {
    return (dispatch) =>
        HttpService.fetchProcessState(processName).then(({ data }) =>
            dispatch({
                type: "PROCESS_STATE_LOADED",
                processState: data,
            }),
        );
}

export function fetchTestFormParameters(processDetails: ScenarioGraph) {
    return (dispatch) =>
        HttpService.getTestFormParameters(processDetails).then(({ data }) => {
            dispatch({
                type: "UPDATE_TEST_FORM_PARAMETERS",
                testFormParameters: data,
            });
        });
}

export function displayTestCapabilities(processDetails: ScenarioGraph) {
    return (dispatch) =>
        HttpService.getTestCapabilities(processDetails).then(({ data }) =>
            dispatch({
                type: "UPDATE_TEST_CAPABILITIES",
                capabilities: data,
            }),
        );
}

export function displayCurrentProcessVersion(processName: ProcessName) {
    return fetchScenario(processName);
}

export function displayScenarioVersion(processName: ProcessName, versionId: ProcessVersionId): ThunkAction {
    return async (dispatch, getState) => {
        await dispatch(fetchScenario(processName, versionId));
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

export function hideRunProcessDetails() {
    return { type: "HIDE_RUN_PROCESS_DETAILS" };
}

export function addAttachment(processName: ProcessName, processVersionId: ProcessVersionId, file: File) {
    return (dispatch) =>
        HttpService.addAttachment(processName, processVersionId, file).then(() => dispatch(displayProcessActivity(processName)));
}
