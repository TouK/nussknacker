import NodeUtils from "../../components/graph/NodeUtils";
import { getScenarioGraph } from "../../reducers/selectors/graph";
import { ThunkAction } from "../reduxTypes";
import { deleteNodes } from "./node";
import { batchGroupBy } from "../../reducers/graph/batchGroupBy";

type Callback = () => void;

type ToggleSelectionAction = { type: "TOGGLE_SELECTION"; nodeIds: string[] };
type ExpandSelectionAction = { type: "EXPAND_SELECTION"; nodeIds: string[] };
type ResetSelectionAction = { type: "RESET_SELECTION"; nodeIds: string[] };

export type SelectionActions = ToggleSelectionAction | ExpandSelectionAction | ResetSelectionAction;

export function copySelection(copyFunction: Callback): ThunkAction {
    return (dispatch) => {
        copyFunction();
        return dispatch({
            type: "COPY_SELECTION",
        });
    };
}

export function cutSelection(cutFunction: Callback): ThunkAction {
    return (dispatch) => {
        cutFunction();
        return dispatch({
            type: "CUT_SELECTION",
        });
    };
}

export function pasteSelection(pasteFunction: Callback): ThunkAction {
    return (dispatch) => {
        pasteFunction();
        return dispatch({
            type: "PASTE_SELECTION",
        });
    };
}

export function deleteSelection(selectionState: string[]): ThunkAction {
    return (dispatch, getState) => {
        const scenarioGraph = getScenarioGraph(getState());
        const selectedNodes = NodeUtils.getAllNodesById(selectionState, scenarioGraph).map((n) => n.id);

        batchGroupBy.startOrContinue();
        dispatch(deleteNodes(selectedNodes));
        dispatch({
            type: "DELETE_SELECTION",
        });
        batchGroupBy.end();
    };
}

//remove browser text selection to avoid interference with "copy"
const clearTextSelection = () => window.getSelection().removeAllRanges();

export function toggleSelection(...nodeIds: string[]): ThunkAction {
    return (dispatch) => {
        batchGroupBy.end();
        clearTextSelection();
        dispatch({ type: "TOGGLE_SELECTION", nodeIds });
    };
}

export function expandSelection(...nodeIds: string[]): ThunkAction {
    return (dispatch) => {
        batchGroupBy.end();
        clearTextSelection();
        dispatch({ type: "EXPAND_SELECTION", nodeIds });
    };
}

export function resetSelection(...nodeIds: string[]): ThunkAction {
    return (dispatch) => {
        batchGroupBy.end();
        clearTextSelection();
        dispatch({ type: "RESET_SELECTION", nodeIds });
    };
}

export function selectAll(): ThunkAction {
    return (dispatch, getState) => {
        const state = getState();
        const scenarioGraph = getScenarioGraph(state);
        const nodeIds = scenarioGraph.nodes.map((n) => n.id);
        dispatch(resetSelection(...nodeIds));
    };
}
