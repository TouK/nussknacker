import {isEmpty} from "lodash"
import {ThunkAction} from "../../reduxTypes"
import {EventInfo, reportEvent} from "../reportEvent"

export interface ConfirmDialogData {
  text: string,
  confirmText: string,
  denyText: string,
  //TODO: get rid of callbacks in store
  onConfirmCallback: () => void,
}

export function toggleConfirmDialog(
  text: string,
  action: () => void,
  confirmText = "Yes",
  denyText = "No",
  event?: EventInfo,
): ThunkAction {
  return (dispatch) => {
    if (!isEmpty(event)) {
      dispatch(reportEvent(event))
    }
    return action()
  }
}
