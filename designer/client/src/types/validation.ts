import { TypingInfo, TypingResult, UIParameter } from "./definition";

export type ValidationResult = {
    validationErrors?: ValidationErrors[];
    validationWarnings?: ValidationWarnings[];
    nodeResults: NodeResults;
    errors: ValidationErrors;
    warnings?: Pick<ValidationErrors, "invalidNodes">;
};

export type NodeResults = Record<string, NodeTypingData>;

export type NodeTypingData = {
    variableTypes: VariableTypes;
    parameters?: UIParameter[];
    typingInfo: TypingInfo;
};

export type VariableTypes = Record<string, TypingResult>;

export type ValidationWarnings = {
    invalidNodes: Record<string, NodeValidationError[]>;
};

export type ValidationErrors = {
    invalidNodes: Record<string, NodeValidationError[]>;
    processPropertiesErrors: NodeValidationError[];
    globalErrors: GlobalValidationError[];
};

export type GlobalValidationError = {
    error: NodeValidationError;
    nodeIds: string[];
};

type ErrorType = "RenderNotAllowed" | "SaveNotAllowed" | "SaveAllowed";

type CellError = {
    columnName: string;
    rowIndex: number;
    errorMessage: string;
};
type TabularDataDefinitionParserErrorDetails = {
    cellErrors: CellError[];
    type: "TabularDataDefinitionParserErrorDetails";
};

type Details = TabularDataDefinitionParserErrorDetails;

export interface NodeValidationError {
    fieldName: string;
    message: string;
    description: string;
    typ: string;
    details?: Details;
    errorType: ErrorType;
}
