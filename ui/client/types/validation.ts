import {TypingResult, UIParameter} from "./definition"

export type ValidationResult = {
    validationErrors: ValidationErrors[],
    validationWarnings: ValidationWarnings[],
    nodeResults: NodeResults,
    errors?: ValidationErrors,
}

export type NodeResults = Record<string, NodeTypingData>

export type NodeTypingData = {
    variableTypes: VariableTypes,
    parameters?: UIParameter[],
    typingInfo: Record<string, TypingResult>,
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

export type NodeValidationError = {
    typ: string,
    message: string,
    description: string,
    fieldName?: string,
    errorType: ErrorType,
}

type ErrorType = "RenderNotAllowed"|"SaveNotAllowed"|"SaveAllowed"
