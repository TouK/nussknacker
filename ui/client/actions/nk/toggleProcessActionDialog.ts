import {ThunkAction} from "../reduxTypes"
import {reportEvent} from "./reportEvent"
import {ProcessId} from "../../types"

export type ToggleProcessActionModalAction = {
  type: "TOGGLE_PROCESS_ACTION_MODAL",
  message: string,
  action: (processId: ProcessId, comment: string) => void,
  displayWarnings: boolean,
}

export function toggleProcessActionDialog(
  message: string,
  action: (processId: ProcessId, comment: string) => void,
  displayWarnings: boolean,
): ThunkAction {
  return (dispatch) => {
    dispatch(reportEvent({
      category: "right_panel",
      action: "button_click",
      name: message,
    }))

    return dispatch({
      type: "TOGGLE_PROCESS_ACTION_MODAL",
      message,
      action,
      displayWarnings,
    })
  }
}
