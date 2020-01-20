import {ThunkAction} from "../reduxTypes"
import {reportEvent} from "./reportEvent"

export type ToggleProcessActionModalAction = {
  type: "TOGGLE_PROCESS_ACTION_MODAL";
  message: string;
  action: string;
  displayWarnings: boolean;
}

export function toggleProcessActionDialog(message: string, action: string, displayWarnings: boolean): ThunkAction {
  return (dispatch) => {
    dispatch(reportEvent({
      category: "right_panel",
      action: "button_click",
      name: message,
    }))

    return dispatch({
      type: "TOGGLE_PROCESS_ACTION_MODAL",
      message: message,
      action: action,
      displayWarnings: displayWarnings,
    })
  }
}
