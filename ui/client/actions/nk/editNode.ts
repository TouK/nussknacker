import HttpService from "../../http/HttpService"
import {calculateProcessAfterChange} from "./process"
import {Edge, NodeType, Process, ValidationResult} from "../../types"
import {ThunkAction} from "../reduxTypes"
import {replaceNodeOutputEdges} from "../../components/graph/GraphUtils"

export type EditNodeAction = {
  type: "EDIT_NODE",
  before: NodeType,
  after: NodeType,
  validationResult: ValidationResult,
  processAfterChange: $TodoType,
}
export type RenameProcessAction = {
  type: "PROCESS_RENAME",
  name: string,
}

export function editNode(process: Process, before: NodeType, after: NodeType, outputEdges: Edge[]): ThunkAction {
  return (dispatch) => {
    let changedProcess = process
    if (outputEdges) {
      changedProcess = replaceNodeOutputEdges(process, before.id, outputEdges)
    }

    return dispatch(calculateProcessAfterChange(changedProcess, before, after)).then((process) => {
      return HttpService.validateProcess(process).then((response) => {
        if (outputEdges) {
          dispatch({
            type: "REPLACE_EDGES",
            node: before.id,
            edges: outputEdges,
            validationResult: response.data,
          })
        }
        return dispatch({
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
