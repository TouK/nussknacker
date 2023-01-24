import HttpService from "../../http/HttpService"

export function loadProcessState(processId) {
  return (dispatch) => HttpService.fetchProcessState(processId).then((response) => dispatch({
    type: "PROCESS_STATE_LOADED",
    processState: response.data,
  }))
}

