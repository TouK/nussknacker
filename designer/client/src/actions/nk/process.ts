import { omit } from "lodash/fp";
import { ActionCreators as UndoActionCreators } from "redux-undo";

import { addStickyNotesToNodes } from "../../components/graph/utils/stickyNotesUtils";
import type { ProcessName, ProcessVersionId, Scenario } from "../../components/Process/types";
import { replaceSearchQuery } from "../../containers/hooks/useSearchQuery";
import { getProcessDefinitionData } from "../../reducers/selectors/processDefinitionData";
import type { ProcessDefinitionData, ScenarioGraph } from "../../types";
import type { ThunkAction } from "../reduxTypes";
import HttpService from "./../../http/HttpService";

export type ScenarioActions =
    | { type: "CORRECT_INVALID_SCENARIO"; processDefinitionData: ProcessDefinitionData }
    | { type: "DISPLAY_PROCESS"; scenario: Scenario };

export function fetchProcessToDisplay(processName: ProcessName, versionId?: ProcessVersionId): ThunkAction<Promise<Scenario>> {
    return (dispatch) => {
        dispatch({ type: "PROCESS_FETCH" });

        return HttpService.fetchProcessDetails(processName, versionId).then((response) => {
            const scenario = addStickyNotesToNodes(response.data);
            dispatch(displayTestCapabilities(processName, scenario.scenarioGraph));
            dispatch({
                type: "DISPLAY_PROCESS",
                scenario: scenario,
            });
            return scenario;
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

export function fetchTestFormParameters(processName: ProcessName, scenarioGraph: ScenarioGraph) {
    return (dispatch) =>
        HttpService.getTestFormParameters(processName, scenarioGraph).then(({ data }) => {
            dispatch({
                type: "UPDATE_TEST_FORM_PARAMETERS",
                testFormParameters: data,
            });
        });
}

export function displayTestCapabilities(processName: ProcessName, scenarioGraph: ScenarioGraph) {
    return (dispatch) =>
        HttpService.getTestCapabilities(processName, scenarioGraph).then(({ data }) =>
            dispatch({
                type: "UPDATE_TEST_CAPABILITIES",
                capabilities: data,
            }),
        );
}

export function displayCurrentProcessVersion(processName: ProcessName) {
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

export function hideRunProcessDetails() {
    replaceSearchQuery(omit(["from", "to", "refresh"]));
    return { type: "HIDE_RUN_PROCESS_DETAILS" };
}
