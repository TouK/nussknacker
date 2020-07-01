import {TypingResult} from "./definition"

export type ValidationResult = {
    validationErrors: ValidationErrors[],
    validationWarnings: ValidationWarnings[],
    variableTypes: VariableTypes,
}

export type VariableTypes = Record<string, Record<string, TypingResult>>

export type ValidationWarnings = {
    invalidNodes: Record<string, NodeValidationError[]>,

}

export type ValidationErrors = {
    invalidNodes: Record<string, NodeValidationError[]>,
    processPropertiesErrors: NodeValidationError[],
    globalErrors: NodeValidationError[],
}

export type NodeValidationError = {
    typ: string,
    message: string,
    description: string,
    fieldName?: string,
    errorType: ErrorType,
}

type ErrorType = "RenderNotAllowed"|"SaveNotAllowed"|"SaveAllowed"
