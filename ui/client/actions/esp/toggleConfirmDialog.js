import _ from "lodash"
import {reportEvent} from "./reportEvent"

export function toggleConfirmDialog(isOpen, text, action, confirmText = "Yes", denyText = "No", event) {
  return (dispatch) => {
    !_.isEmpty(event) && dispatch(reportEvent(
        {
          category: event.category,
          action: event.action,
          name: event.name,
        },
    ))

    return dispatch({
      type: "TOGGLE_CONFIRM_DIALOG",
      isOpen: isOpen,
      text: text,
      confirmText: confirmText,
      denyText: denyText,
      onConfirmCallback: action,
    })
  }
}
