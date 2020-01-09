// @flow
import _ from "lodash"
import {ThunkAction, ThunkDispatch} from "redux-thunk"
import type {EventInfo} from "../reportEvent"
import {reportEvent} from "../reportEvent"

export type ToggleConfirmDialogAction = {
  type: "TOGGLE_CONFIRM_DIALOG",
  isOpen: boolean,
  text: string,
  confirmText: string,
  denyText: string,
  onConfirmCallback: $FlowTODO,
}

export function toggleConfirmDialog(
    isOpen: boolean,
    text: string,
    action: string,
    confirmText: string = "Yes",
    denyText: string = "No",
    event: EventInfo,
): ThunkAction {
  return (dispatch: ThunkDispatch) => {
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