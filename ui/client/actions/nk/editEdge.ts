import * as GraphUtils from "../../components/graph/GraphUtils"
import {replaceProcessEdges} from "../../components/graph/GraphUtils"
import HttpService from "../../http/HttpService"
import {Edge, NodeId, Process, ValidationResult} from "../../types"
import {ThunkAction} from "../reduxTypes"

export type EditEdgeAction = {
  type: "EDIT_EDGE",
  before: Edge,
  after: Edge,
  validationResult: ValidationResult,
}

export type ReplaceEdgesAction = {
  type: "REPLACE_EDGES",
  node: NodeId,
  edges: Edge[],
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

export function replaceEdgesWithOrder(process: Process, node: NodeId, edges: Edge[]): ThunkAction {
  return (dispatch) => {
    const changedProcess = replaceProcessEdges(process, node, edges)
    return HttpService.validateProcess(changedProcess).then((response) => {
      dispatch({
        type: "REPLACE_EDGES",
        node,
        edges,
        validationResult: response.data,
      })
    })
  }
}

