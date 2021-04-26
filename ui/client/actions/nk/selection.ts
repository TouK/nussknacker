import {ThunkAction} from "../reduxTypes"
import {deleteNodes} from "./node"
import {reportEvent} from "./reportEvent"

type Event = {category: string, action: string}
type Callback = () => void

type ToggleSelectionAction = {type: "TOGGLE_SELECTION", nodeIds: string[]}
type ExpandSelectionAction = {type: "EXPAND_SELECTION", nodeIds: string[]}
type ResetSelectionAction = {type: "RESET_SELECTION", nodeIds: string[]}

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
  return (dispatch) => {
    dispatch(deleteNodes(selectionState))

    dispatch(reportEvent({
      category: event.category,
      action: event.action,
      name: "delete",
    }))

    return dispatch({
      type: "DELETE_SELECTION",
    })
  }
}

export function toggleSelection(...nodeIds: string[]): SelectionActions {
  return {type: "TOGGLE_SELECTION", nodeIds}
}

export function expandSelection(...nodeIds: string[]): SelectionActions {
  return {type: "EXPAND_SELECTION", nodeIds}
}

export function resetSelection(...nodeIds: string[]): SelectionActions {
  return {type: "RESET_SELECTION", nodeIds}
}
