import { ThunkAction } from "../reduxTypes";
import { Process, ProcessDefinitionData, ProcessId } from "../../types";
import HttpService from "./../../http/HttpService";
import { ProcessType, ProcessVersionId } from "../../components/Process/types";
import { displayProcessActivity } from "./displayProcessActivity";
import { ActionCreators as UndoActionCreators } from "redux-undo";
import { getProcessDefinitionData } from "../../reducers/selectors/settings";

export type ScenarioActions =
    | { type: "CORRECT_INVALID_SCENARIO"; processDefinitionData: ProcessDefinitionData }
    | { type: "DISPLAY_PROCESS"; fetchedProcessDetails: ProcessType };

export function fetchProcessToDisplay(processId: ProcessId, versionId?: ProcessVersionId): ThunkAction<Promise<ProcessType>> {
    return (dispatch) => {
        dispatch({ type: "PROCESS_FETCH" });

        return HttpService.fetchProcessDetails(processId, versionId).then((response) => {
            dispatch(displayTestCapabilities(response.data.json));
            dispatch({
                type: "DISPLAY_PROCESS",
                fetchedProcessDetails: response.data,
            });
            return response.data;
        });
    };
}

export function loadProcessState(processId: ProcessId): ThunkAction {
    return (dispatch) =>
        HttpService.fetchProcessState(processId).then(({ data }) =>
            dispatch({
                type: "PROCESS_STATE_LOADED",
                processState: data,
            }),
        );
}

export function fetchTestFormParameters(processDetails: Process) {
    return (dispatch) =>
        HttpService.getTestFormParameters(processDetails).then(({ data }) => {
            dispatch({
                type: "UPDATE_TEST_FORM_PARAMETERS",
                testFormParameters: data,
            });
        });
}

export function displayTestCapabilities(processDetails: Process) {
    return (dispatch) =>
        HttpService.getTestCapabilities(processDetails).then(({ data }) =>
            dispatch({
                type: "UPDATE_TEST_CAPABILITIES",
                capabilities: data,
            }),
        );
}

export function displayCurrentProcessVersion(processId: ProcessId) {
    return fetchProcessToDisplay(processId);
}

export function displayScenarioVersion(processId: ProcessId, versionId: ProcessVersionId): ThunkAction {
    return async (dispatch, getState) => {
        await dispatch(fetchProcessToDisplay(processId, versionId));
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

export function addAttachment(processId: ProcessId, processVersionId: ProcessVersionId, file: File) {
    return (dispatch) =>
        HttpService.addAttachment(processId, processVersionId, file).then(() => dispatch(displayProcessActivity(processId)));
}
