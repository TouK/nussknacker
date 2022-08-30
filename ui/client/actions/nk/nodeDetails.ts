import {ThunkAction} from "../reduxTypes"
import HttpService from "../../http/HttpService"
import {
  Edge,
  NodeId,
  NodeType,
  NodeValidationError,
  PropertiesType,
  TypingResult,
  UIParameter,
  VariableTypes,
} from "../../types"

import {debounce} from "lodash"
import {
  getFindAvailableBranchVariables,
  getFindAvailableVariables,
  getProcessProperties
} from "../../components/graph/node-modal/NodeDetailsContent/selectors"

export type NodeValidationUpdated = { type: "NODE_VALIDATION_UPDATED", validationData: ValidationData, nodeId: string }
export type NodeValidationClear = { type: "NODE_VALIDATION_CLEAR", nodeId: string }
export type NodeDetailsActions = NodeValidationUpdated | NodeValidationClear

export interface ValidationData {
  parameters?: UIParameter[],
  expressionType?: TypingResult,
  validationErrors: NodeValidationError[],
  validationPerformed: boolean,
}

export interface ValidationRequest {
  nodeData: NodeType,
  variableTypes: VariableTypes,
  branchVariableTypes: Record<string, VariableTypes>,
  processProperties: PropertiesType,
  outgoingEdges: Edge[],
}

function nodeValidationDataUpdated(validationData: ValidationData, nodeId: string): NodeValidationUpdated {
  return {type: "NODE_VALIDATION_UPDATED", validationData, nodeId}
}

export function nodeValidationDataClear(nodeId: string): NodeValidationClear {
  return {type: "NODE_VALIDATION_CLEAR", nodeId}
}

//we don't return ThunkAction here as it would not work correctly with debounce
//TODO: use sth better, how long should be timeout?
const validate = debounce(async (processId: string, validationRequestData: ValidationRequest, callback: (data: ValidationData, nodeId: NodeId) => void) => {
  const nodeId = validationRequestData.nodeData.id
  const {data} = await HttpService.validateNode(processId, validationRequestData)
  callback(data, nodeId)
}, 500, {leading: true})

export function validateNodeData(processId: string, validationRequestData: Omit<ValidationRequest, "branchVariableTypes" | "processProperties" | "variableTypes">): ThunkAction {
  return (dispatch, getState) => {
    //Properties are "special types" which are not compatible with NodeData in BE
    const {nodeData} = validationRequestData
    if (nodeData.type && nodeData.type !== "Properties") {
      const state = getState()
      validate(processId, {
        ...validationRequestData,
        branchVariableTypes: getFindAvailableBranchVariables(state)(nodeData.id),
        processProperties: getProcessProperties(state),
        variableTypes: getFindAvailableVariables(state)(nodeData.id),
      }, (data, nodeId) => {
        dispatch(nodeValidationDataUpdated(data, nodeId))
      })
    }
  }
}

