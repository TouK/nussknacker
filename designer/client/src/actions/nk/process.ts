import { omit } from "lodash/fp";
import { ActionCreators as UndoActionCreators } from "redux-undo";
import { ProcessName, ProcessVersionId, Scenario } from "../../components/Process/types";
import { replaceSearchQuery } from "../../containers/hooks/useSearchQuery";
import { getProcessDefinitionData } from "../../reducers/selectors/settings";
import { ProcessDefinitionData, ScenarioGraph } from "../../types";
import { ThunkAction } from "../reduxTypes";
import HttpService from "./../../http/HttpService";
import { layoutChanged, Position } from "./ui/layout";
import { batchGroupBy } from "../../reducers/graph/batchGroupBy";
import { flushSync } from "react-dom";
import { Dimensions, StickyNote } from "../../common/StickyNote";
import { cloneDeep } from "lodash";

export type ScenarioActions =
    | { type: "CORRECT_INVALID_SCENARIO"; processDefinitionData: ProcessDefinitionData }
    | { type: "DISPLAY_PROCESS"; scenario: Scenario };

export function fetchProcessToDisplay(processName: ProcessName, versionId?: ProcessVersionId): ThunkAction<Promise<Scenario>> {
    return (dispatch) => {
        dispatch({ type: "PROCESS_FETCH" });

        return HttpService.fetchProcessDetails(processName, versionId).then((response) => {
            dispatch(displayTestCapabilities(processName, response.data.scenarioGraph));
            dispatch(fetchStickyNotesForScenario(processName, response.data.processVersionId));
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

export function fetchStickyNotesForScenario(scenarioName: string, scenarioVersionId: number): ThunkAction {
    return (dispatch) => {
        HttpService.getStickyNotes(scenarioName, scenarioVersionId).then((stickyNotes) => {
            dispatch({ type: "STICKY_NOTES_UPDATED", stickyNotes: stickyNotes.data });
        });
    };
}

export function stickyNoteUpdated(scenarioName: string, scenarioVersionId: number, stickyNote: StickyNote): ThunkAction {
    return (dispatch) => {
        HttpService.updateStickyNote(scenarioName, scenarioVersionId, stickyNote).then((_) => {
            HttpService.getStickyNotes(scenarioName, scenarioVersionId).then((stickyNotes) => {
                flushSync(() => {
                    dispatch({ type: "STICKY_NOTES_UPDATED", stickyNotes: stickyNotes.data });
                    dispatch(layoutChanged());
                });
            });
        });
        batchGroupBy.end();
    };
}

export function stickyNoteAdded(scenarioName: string, scenarioVersionId: number, position: Position, dimensions: Dimensions): ThunkAction {
    return (dispatch) => {
        HttpService.addStickyNote(scenarioName, scenarioVersionId, position, dimensions).then((_) => {
            HttpService.getStickyNotes(scenarioName, scenarioVersionId).then((stickyNotes) => {
                flushSync(() => {
                    dispatch({ type: "STICKY_NOTES_UPDATED", stickyNotes: stickyNotes.data });
                    dispatch(layoutChanged());
                });
            });
        });
        batchGroupBy.end();
    };
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
