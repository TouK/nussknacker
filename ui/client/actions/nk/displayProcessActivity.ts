import HttpService from "../../http/HttpService"
import {$TodoType} from "../migrationTypes"
import {ThunkAction} from "../reduxTypes"

export type DisplayProcessActivityAction = {
  type: "DISPLAY_PROCESS_ACTIVITY";
  comments: $TodoType[];
  attachments: $TodoType[];
}

export function displayProcessActivity(processId: string): ThunkAction {
  return (dispatch) => {
    return HttpService.fetchProcessActivity(processId).then((response) => {
      return dispatch({
        type: "DISPLAY_PROCESS_ACTIVITY",
        comments: response.data.comments,
        attachments: response.data.attachments,
      })
    })
  }
}
