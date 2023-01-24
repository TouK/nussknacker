import HttpService from "../../http/HttpService"
import {displayProcessActivity} from "./displayProcessActivity"

export function loadProcessState(processId) {
  return (dispatch) => HttpService.fetchProcessState(processId).then((response) => dispatch({
    type: "PROCESS_STATE_LOADED",
    processState: response.data,
  }))
}

export function addAttachment(processId, processVersionId, comment) {
  return (dispatch) => {
    return HttpService.addAttachment(processId, processVersionId, comment).then(() => {
      return dispatch(displayProcessActivity(processId))
    })
  }
}
