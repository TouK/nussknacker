import { omit } from "lodash/fp";
import { ActionCreators as UndoActionCreators } from "redux-undo";
import { ProcessName, ProcessVersionId, Scenario } from "../../components/Process/types";
import { replaceSearchQuery } from "../../containers/hooks/useSearchQuery";
import { getProcessDefinitionData } from "../../reducers/selectors/settings";
import { ProcessDefinitionData, ScenarioGraph } from "../../types";
import { ThunkAction } from "../reduxTypes";
import HttpService from "./../../http/HttpService";
import { partition } from "lodash";
import { StickyNoteType } from "../../types/stickyNote";

export type ScenarioActions =
    | { type: "CORRECT_INVALID_SCENARIO"; processDefinitionData: ProcessDefinitionData }
    | { type: "DISPLAY_PROCESS"; scenario: Scenario };

// We add sticky notes to nodes to handle them as nodes on FE but on BE they are present just to be saved, we do not process them as nodes.
// Thanks to that we do not have to handle edge-cases with 'loose nodes'
// `addStickyNotesToNodes` - Merge stickyNotes with nodes
// `extractStickyNotesFromNodes` - Split stickyNotes from nodes
export function addStickyNotesToNodes(data: Scenario): Scenario {
    const stickyNotesWithType = data.scenarioGraph.stickyNotes.map((name) => ({
        ...name,
        type: StickyNoteType,
    }));
    return {
        ...data,
        scenarioGraph: {
            ...data.scenarioGraph,
            nodes: [...data.scenarioGraph.nodes, ...stickyNotesWithType],
        },
    };
}

export function extractStickyNotesFromNodes(graph: ScenarioGraph): ScenarioGraph {
    const [stickyNotes, nodes] = partition(graph.nodes, (node) => node.type === StickyNoteType);
    return {
        ...graph,
        nodes: nodes,
        stickyNotes: stickyNotes,
    };
}

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
