import {ThunkAction} from "../reduxTypes"
import HttpService from "../../http/HttpService"
import _ from "lodash"

export type NodeDataUpdated = { type: "NodeDataUpdated", nodeData: any}
export type NodeValidationUpdated = { type: "NODE_VALIDATION_UPDATED", validationData: any}
export type NodeDetailsActions = NodeDataUpdated | NodeValidationUpdated

export type NodeValidationData = {
    parameters? : Map<string, ValidationContext>,
    validationErrors: Array<ValidationError>,
}

export type ValidationContext = $TodoType
export type ValidationError = $TodoType

function nodeValidationDataUpdated(validationData: any): NodeValidationUpdated {
  return {type: "NODE_VALIDATION_UPDATED", validationData: validationData}
}

function validate(processId, node, dispatch) {
  HttpService.validateNode(processId, node).then(data => dispatch(nodeValidationDataUpdated(data.data)))
}

//TODO: use sth better?
const debouncedValidate = _.debounce(validate, 250, {leading: true, trailing: true})

export function updateNodeData(processId: string, variableTypes: any, nodeData: any): ThunkAction {
  return (dispatch) => debouncedValidate(processId, {
    nodeData, variableTypes}, dispatch)

}
 
