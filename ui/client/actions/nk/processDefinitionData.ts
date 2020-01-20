import HttpService from "../../http/HttpService"
import {$TodoType} from "../migrationTypes"
import {ThunkAction} from "../reduxTypes"

export type ProcessDefinitionDataAction = {
  type: "PROCESS_DEFINITION_DATA";
  processDefinitionData: $TodoType;
}

export function processDefinitionData(data: $TodoType): ProcessDefinitionDataAction {
  return {
    type: "PROCESS_DEFINITION_DATA",
    processDefinitionData: data,
  }
}

type ProcessingType = string

export function fetchProcessDefinition(processingType: ProcessingType, isSubprocess: boolean, subprocessVersions: $TodoType): ThunkAction {
  return (dispatch) => {
    return HttpService.fetchProcessDefinitionData(processingType, isSubprocess, subprocessVersions).then((response) => (
            dispatch(processDefinitionData(response.data))
        ),
    )
  }
}
