import * as GraphUtils from "../../components/graph/GraphUtils"
import HttpService from "../../http/HttpService"
import {NodeType, Process, ValidationResult} from "../../types"
import {ThunkAction} from "../reduxTypes"

export type EditEdgeAction = {
  type: "EDIT_EDGE",
  before: NodeType,
  after: NodeType,
  validationResult: ValidationResult,
}

export function editEdge(process: Process, before: NodeType, after: NodeType): ThunkAction {
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
