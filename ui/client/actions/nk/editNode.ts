import HttpService from "../../http/HttpService"
import {calculateProcessAfterChange} from "./process"
import {NodeType, Process, ValidationResult} from "../../types"
import {ThunkAction} from "../reduxTypes"

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

export function editNode(process: Process, before: NodeType, after: NodeType): ThunkAction {
  return (dispatch) => {
    return dispatch(calculateProcessAfterChange(process, before, after)).then((process) => {
      return HttpService.validateProcess(process).then((response) => {
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
