import HttpService from "../../http/HttpService"
import {displayProcessActivity} from "./displayProcessActivity"
import {ProcessId} from "../../types";
import {ThunkAction} from "../reduxTypes";

export function addComment(processId: ProcessId, processVersionId, comment): ThunkAction {
  return (dispatch) => {
    return HttpService.addComment(processId, processVersionId, comment).then(() => {
      return dispatch(displayProcessActivity(processId))
    })
  }
}

export function deleteComment(processId: ProcessId, commentId): ThunkAction {
  return (dispatch) => {
    return HttpService.deleteComment(processId, commentId).then(() => {
      return dispatch(displayProcessActivity(processId))
    })
  }
}
