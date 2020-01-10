// @flow

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