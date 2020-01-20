import {deleteNodes} from "./node"
import {reportEvent} from "./reportEvent"

export function copySelection(copyFunction, event) {
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

export function cutSelection(cutFunction, event) {
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

export function pasteSelection(pasteFunction, event) {
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

export function deleteSelection(selectionState, event) {
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

export function expandSelection(nodeId) {
  return {type: "EXPAND_SELECTION", nodeId}
}

export function resetSelection(nodeId) {
  return {type: "RESET_SELECTION", nodeId}
}
