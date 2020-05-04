import HttpService from "../../http/HttpService"
import {calculateProcessAfterChange} from "./process"
import {NodeType} from "../../types"
import {ThunkAction} from "../reduxTypes"
import {ProcessToDisplayState} from "../../reducers/graphState"

export type EditNodeAction = {
  type: "EDIT_NODE",
  before: NodeType,
  after: NodeType,
  validationResult: $TodoType,
  processAfterChange: $TodoType,
}

export function editNode(process: ProcessToDisplayState, before: NodeType, after: NodeType): ThunkAction {
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
