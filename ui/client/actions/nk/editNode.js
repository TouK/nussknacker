import HttpService from "../../http/HttpService"
import {calculateProcessAfterChange} from "./process"

export function editNode(process, before, after) {
  return (dispatch) => {
    const processAfterChange = calculateProcessAfterChange(process, before, after, dispatch)
    return processAfterChange.then((process) => {
      return HttpService.validateProcess(process).then((response) => {
        dispatch({
          type: "EDIT_NODE",
          before: before,
          after: after,
          validationResult: response.data,
          processAfterChange: process,
        })
      })
    })
  }
}
