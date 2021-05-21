import * as GraphUtils from "../../components/graph/GraphUtils"
import HttpService from "../../http/HttpService"
import {Edge, Process, ValidationResult} from "../../types"
import {ThunkAction} from "../reduxTypes"

export type EditEdgeAction = {
  type: "EDIT_EDGE",
  before: Edge,
  after: Edge,
  validationResult: ValidationResult,
}

export function editEdge(process: Process, before: Edge, after: Edge): ThunkAction {
  return (dispatch) => {
    const changedProcess = GraphUtils.mapProcessWithNewEdge(process, before, after)
    return HttpService.validateProcess(changedProcess).then((response) => {
      dispatch({
        type: "EDIT_EDGE",
        before: before,
        after: after,
        validationResult: response.data,
      })
    })
  }
}
