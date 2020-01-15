import HttpService from "../../http/HttpService"
import {displayProcessActivity} from "./displayProcessActivity"

export function addComment(processId, processVersionId, comment) {
  return (dispatch) => {
    return HttpService.addComment(processId, processVersionId, comment).then(() => {
      return dispatch(displayProcessActivity(processId))
    })
  }
}

export function deleteComment(processId, commentId) {
  return (dispatch) => {
    return HttpService.deleteComment(processId, commentId).then(() => {
      return dispatch(displayProcessActivity(processId))
    })
  }
}
