import NodeUtils from "../../components/graph/NodeUtils"
import {getProcessToDisplay} from "../../reducers/selectors/graph"
import {ThunkAction} from "../reduxTypes"
import {deleteNodes} from "./node"
import {reportEvent} from "./reportEvent"

type Event = { category: string, action: string }
type Callback = () => void

type ToggleSelectionAction = { type: "TOGGLE_SELECTION", nodeIds: string[] }
type ExpandSelectionAction = { type: "EXPAND_SELECTION", nodeIds: string[] }
type ResetSelectionAction = { type: "RESET_SELECTION", nodeIds: string[] }

export type SelectionActions =
  | ToggleSelectionAction
  | ExpandSelectionAction
  | ResetSelectionAction

export function copySelection(copyFunction: Callback, event: Event): ThunkAction {
  return (dispatch) => {
    copyFunction()

    dispatch(reportEvent({
      category: event.category,
      action: event.action,
      name: "copy",
    }))

    return dispatch({
      type: "COPY_SELECTION",
    })
  }
}

export function cutSelection(cutFunction: Callback, event: Event): ThunkAction {
  return (dispatch) => {
    cutFunction()

    dispatch(reportEvent({
      category: event.category,
      action: event.action,
      name: "cut",
    }))

    return dispatch({
      type: "CUT_SELECTION",
    })
  }
}

export function pasteSelection(pasteFunction: Callback, event: Event): ThunkAction {
  return (dispatch) => {
    pasteFunction()

    dispatch(reportEvent({
      category: event.category,
      action: event.action,
      name: "paste",
    }))

    return dispatch({
      type: "PASTE_SELECTION",
    })
  }
}

export function deleteSelection(selectionState: string[], event: Event): ThunkAction {
  return (dispatch, getState) => {
    const process = getProcessToDisplay(getState())
    const selectedNodes = NodeUtils.getAllNodesById(selectionState, process).map(n => n.id)

    dispatch(reportEvent({
      category: event.category,
      action: event.action,
      name: "delete",
    }))

    dispatch(deleteNodes(selectedNodes))

    return dispatch({
      type: "DELETE_SELECTION",
    })
  }
}

//remove browser text selection to avoid interference with "copy"
const clearTextSelection = () => window.getSelection().removeAllRanges()

export function toggleSelection(...nodeIds: string[]): ThunkAction {
  return dispatch => {
    clearTextSelection()
    dispatch({type: "TOGGLE_SELECTION", nodeIds})
  }
}

export function expandSelection(...nodeIds: string[]): ThunkAction {
  return dispatch => {
    clearTextSelection()
    dispatch({type: "EXPAND_SELECTION", nodeIds})
  }
}

export function resetSelection(...nodeIds: string[]): ThunkAction {
  return dispatch => {
    clearTextSelection()
    dispatch({type: "RESET_SELECTION", nodeIds})
  }
}

export function selectAll(): ThunkAction {
  return (dispatch, getState) => {
    const state = getState()
    const process = getProcessToDisplay(state)
    const nodeIds = process.nodes.map(n => n.id)
    dispatch(resetSelection(...nodeIds))
  }
}
