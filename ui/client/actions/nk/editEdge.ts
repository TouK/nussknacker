import * as GraphUtils from "../../components/graph/GraphUtils"
import HttpService from "../../http/HttpService"
import {NodeType} from "../../types"
import {ThunkAction} from "../reduxTypes"
import {ProcessToDisplayState} from "../../reducers/graphState"

export type EditEdgeAction = {
  type: "EDIT_EDGE",
  before: NodeType,
  after: NodeType,
  validationResult: $TodoType,
}

export function editEdge(process: ProcessToDisplayState, before: NodeType, after: NodeType): ThunkAction {
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
