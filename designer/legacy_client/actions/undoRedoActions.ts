import {reportEvent, EventInfo} from "./nk"
import {ThunkAction} from "./reduxTypes"

export function undo(eventInfo: Partial<EventInfo>): ThunkAction {
  return (dispatch) => {
    dispatch(reportEvent({
      category: eventInfo.category,
      action: eventInfo.action,
      name: "undo",
    }))

    dispatch({
      type: "UNDO",
    })
  }
}

export function redo(eventInfo: Partial<EventInfo>): ThunkAction {
  return (dispatch) => {
    dispatch(reportEvent({
      category: eventInfo.category,
      action: eventInfo.action,
      name: "redo",
    }))

    dispatch({
      type: "REDO",
    })
  }
}

export function clear(): UndoRedoActions {
  return {type: "CLEAR"}
}

export type UndoRedoActions =
  | { type: "UNDO" }
  | { type: "REDO" }
  | { type: "CLEAR" }
  | { type: "JUMP_TO_STATE", direction: "PAST" | "FUTURE", index: number }
