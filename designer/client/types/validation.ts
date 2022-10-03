import {TypingInfo, TypingResult, UIParameter} from "./definition"
import {Error} from "../components/graph/node-modal/editors/Validators"

export type ValidationResult = {
  validationErrors: ValidationErrors[],
  validationWarnings: ValidationWarnings[],
  nodeResults: NodeResults,
  errors?: ValidationErrors,
  warnings?,
}

export type NodeResults = Record<string, NodeTypingData>

export type NodeTypingData = {
  variableTypes: VariableTypes,
  parameters?: UIParameter[],
  typingInfo: TypingInfo,
}

export type VariableTypes = Record<string, TypingResult>

export type ValidationWarnings = {
  invalidNodes: Record<string, NodeValidationError[]>,

}

export type ValidationErrors = {
  invalidNodes: Record<string, NodeValidationError[]>,
  processPropertiesErrors: NodeValidationError[],
  globalErrors: NodeValidationError[],
}

export interface NodeValidationError extends Error {
  errorType: ErrorType,
}

type ErrorType = "RenderNotAllowed" | "SaveNotAllowed" | "SaveAllowed"
