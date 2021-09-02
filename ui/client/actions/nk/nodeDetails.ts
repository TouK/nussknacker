import {ThunkAction, ThunkDispatch} from "../reduxTypes"
import HttpService from "../../http/HttpService"
import {NodeValidationError, PropertiesType, VariableTypes, NodeType, UIParameter, TypingResult} from "../../types"

import {debounce} from "lodash"

export type NodeValidationUpdated = { type: "NODE_VALIDATION_UPDATED", validationData: ValidationData, nodeId: string}
export type NodeDetailsActions = NodeValidationUpdated

export type ValidationData = {
    parameters? : UIParameter[],
    expressionType?: TypingResult,
    validationErrors: NodeValidationError[],
    validationPerformed: boolean,
}

type ValidationRequest = {
    nodeData: NodeType,
    variableTypes: VariableTypes,
    branchVariableTypes: Record<string, VariableTypes>,
    processProperties: PropertiesType,
}

function nodeValidationDataUpdated(validationData: ValidationData, nodeId: string): NodeValidationUpdated {
  return {type: "NODE_VALIDATION_UPDATED", validationData, nodeId}
}

//we don't return ThunkAction here as it would not work correctly with debounce
function validate(processId: string, request: ValidationRequest, dispatch: ThunkDispatch) {
  HttpService.validateNode(processId, request).then(data => dispatch(nodeValidationDataUpdated(data.data, request.nodeData.id)))
}

//TODO: use sth better, how long should be timeout?
const debouncedValidate = debounce(validate, 500)

export function updateNodeData(processId: string, variableTypes: VariableTypes, branchVariableTypes: Record<string, VariableTypes>, nodeData: NodeType, processProperties: PropertiesType): ThunkAction {
  //groups and Properties are "special types" which are not compatible with NodeData in BE
  if (nodeData.type && nodeData.type !== "_group" && nodeData.type !== "Properties") {
    return (dispatch) => debouncedValidate(processId, {
      nodeData, variableTypes, processProperties, branchVariableTypes}, dispatch)
  } else {
    return () => {/* ignore invocation */}
  }

}

