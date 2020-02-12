import HttpService from "../../http/HttpService"
import {Attachment} from "../../reducers/processActivity"
import {ThunkAction} from "../reduxTypes"

export type DisplayProcessActivityAction = {
  type: "DISPLAY_PROCESS_ACTIVITY",
  comments: $TodoType[],
  attachments: Attachment[],
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
