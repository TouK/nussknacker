import {ThunkAction} from "../reduxTypes"
import HttpService from "../../http/HttpService"
import {
  Expression,
  NodeValidationError,
  PropertiesType,
  TypingResult,
  VariableTypes,
} from "../../types"

import {debounce} from "lodash"

export type NodeValidationUpdated = { type: "GENERIC_ACTION_VALIDATION_UPDATED", validationData: GenericValidationData}
export type NodeValidationClear = { type: "NODE_VALIDATION_CLEAR", nodeId: string }
export type NodeDetailsActions = NodeValidationUpdated | NodeValidationClear

export interface GenericValidationData {
  validationErrors: NodeValidationError[],
  validationPerformed: boolean,
}

export interface UIValueParameter {
  name: string,
  typ: TypingResult,
  expression: Expression
}

export interface GenericValidationRequest {
  parameters: UIValueParameter[],
  variableTypes: VariableTypes,
  processProperties: PropertiesType
}

function nodeGenericValidationDataUpdated(validationData: GenericValidationData): NodeValidationUpdated {
  return {type: "GENERIC_ACTION_VALIDATION_UPDATED", validationData}
}

const validate = debounce(async (processId: string, validationRequestData: GenericValidationRequest, callback: (data: GenericValidationData) => void) => {
  const {data} = await HttpService.validateGenericActionParameters(processId, validationRequestData)
  callback(data)
}, 500)

export function validateGenericActionParameters(processId: string, validationRequestData: GenericValidationRequest): ThunkAction {
  return (dispatch) =>
    validate(processId, validationRequestData, (data) => {
      dispatch(nodeGenericValidationDataUpdated(data))
    })
}

