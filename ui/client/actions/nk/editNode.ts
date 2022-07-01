import HttpService from "../../http/HttpService"
import {Edge, NodeType, Process, ValidationResult} from "../../types"
import {ThunkAction} from "../reduxTypes"
import {calculateProcessAfterChange} from "./calculateProcessAfterChange"

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

export function editNode(processBefore: Process, before: NodeType, after: NodeType, outputEdges?: Edge[]): ThunkAction {
  return async (dispatch) => {
    const process = await dispatch(calculateProcessAfterChange(processBefore, before, after, outputEdges))
    const response = await HttpService.validateProcess(process)

    return dispatch({
      type: "EDIT_NODE",
      before: before,
      after: after,
      validationResult: response.data,
      processAfterChange: process,
    })
  }
}
