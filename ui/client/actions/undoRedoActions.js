import {reportEvent} from "./nk/reportEvent"

export function undo(eventInfo) {
  return (dispatch) => {
    dispatch(reportEvent({
      category: eventInfo.category,
      action: eventInfo.action,
      name: "undo",
    }))

    return dispatch({
      type: "UNDO",
    })
  }
}

export function redo(eventInfo) {
  return (dispatch) => {
    dispatch(reportEvent({
      category: eventInfo.category,
      action: eventInfo.action,
      name: "redo",
    }))

    return dispatch({
      type: "REDO",
    })
  }
}

export function clear() {
  return {type: "CLEAR"}
}
