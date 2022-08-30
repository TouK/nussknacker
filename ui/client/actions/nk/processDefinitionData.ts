import HttpService from "../../http/HttpService"
import {ProcessDefinitionData} from "../../types"
import {ThunkAction} from "../reduxTypes"

export type ProcessDefinitionDataAction = {
  type: "PROCESS_DEFINITION_DATA",
  processDefinitionData: ProcessDefinitionData,
}

export function processDefinitionData(data: ProcessDefinitionData): ProcessDefinitionDataAction {
  return {
    type: "PROCESS_DEFINITION_DATA",
    processDefinitionData: data,
  }
}

export type ProcessingType = string

export function fetchProcessDefinition(processingType: ProcessingType, isSubprocess?: boolean): ThunkAction<Promise<ProcessDefinitionDataAction>> {
  return (dispatch) => {
    return HttpService.fetchProcessDefinitionData(processingType, isSubprocess).then(
      (response) => dispatch(processDefinitionData(response.data))
    )
  }
}
