import HttpService from "../../http/HttpService"
import {$FlowTODO} from "../migrationTypes"
import {ThunkAction} from "../reduxTypes"

export type ProcessDefinitionDataAction = {
  type: "PROCESS_DEFINITION_DATA",
  processDefinitionData: $FlowTODO,
}

export function processDefinitionData(data: $FlowTODO): ProcessDefinitionDataAction {
  return {
    type: "PROCESS_DEFINITION_DATA",
    processDefinitionData: data,
  }
}

type ProcessingType = string

export function fetchProcessDefinition(processingType: ProcessingType, isSubprocess: boolean, subprocessVersions: $FlowTODO): ThunkAction {
  return (dispatch) => {
    return HttpService.fetchProcessDefinitionData(processingType, isSubprocess, subprocessVersions).then((response) => (
            dispatch(processDefinitionData(response.data))
        ),
    )
  }
}