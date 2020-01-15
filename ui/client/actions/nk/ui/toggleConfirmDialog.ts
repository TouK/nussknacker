import {isEmpty} from "lodash"
import {$TodoType} from "../../migrationTypes"
import {ThunkAction} from "../../reduxTypes"
import {EventInfo, reportEvent} from "../reportEvent"

export type ToggleConfirmDialogAction = {
  type: "TOGGLE_CONFIRM_DIALOG";
  isOpen: boolean;
  text: string;
  confirmText: string;
  denyText: string;
  onConfirmCallback: $TodoType;
}

export function toggleConfirmDialog(
    isOpen: boolean,
    text: string,
    action: string,
    confirmText = "Yes",
    denyText = "No",
    event: EventInfo,
): ThunkAction {
  return (dispatch) => {
    !isEmpty(event) && dispatch(reportEvent(event))

    return dispatch({
      type: "TOGGLE_CONFIRM_DIALOG",
      onConfirmCallback: action,
      isOpen,
      text,
      confirmText,
      denyText,
    })
  }
}
