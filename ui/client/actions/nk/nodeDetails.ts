import {ThunkAction, ThunkDispatch} from "../reduxTypes"
import HttpService from "../../http/HttpService"
import {
  Edge,
  NodeType,
  NodeValidationError,
  PropertiesType,
  TypingResult,
  UIParameter,
  VariableTypes,
} from "../../types"

import {debounce} from "lodash"

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
function validate(processId: string, validationRequestData: ValidationRequest, dispatch: ThunkDispatch) {
  HttpService.validateNode(processId, validationRequestData).then(data => dispatch(nodeValidationDataUpdated(data.data, validationRequestData.nodeData.id)))
}

//TODO: use sth better, how long should be timeout?
const debouncedValidate = debounce(validate, 500)

export function validateNodeData(processId: string, validationRequestData: ValidationRequest): ThunkAction {
  //Properties are "special types" which are not compatible with NodeData in BE
  const {nodeData} = validationRequestData
  if (nodeData.type && nodeData.type !== "Properties") {
    return (dispatch) => debouncedValidate(processId, validationRequestData, dispatch)
  } else {
    return () => {/* ignore invocation */
    }
  }

}

