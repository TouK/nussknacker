import { ThunkAction } from "../reduxTypes";
import HttpService from "../../http/HttpService";
import { Expression, NodeValidationError, TypingResult, VariableTypes } from "../../types";

import { debounce } from "lodash";

type GenericActionValidationUpdated = { type: "GENERIC_ACTION_VALIDATION_UPDATED"; validationData: GenericValidationData };
export type GenericActionActions = GenericActionValidationUpdated;

export interface GenericValidationData {
    validationErrors: NodeValidationError[];
    validationPerformed: boolean;
}

export interface UIValueParameter {
    name: string;
    typ: TypingResult;
    expression: Expression;
}

export interface GenericValidationRequest {
    parameters: UIValueParameter[];
    variableTypes: VariableTypes;
}

function nodeGenericValidationDataUpdated(validationData: GenericValidationData): GenericActionValidationUpdated {
    return { type: "GENERIC_ACTION_VALIDATION_UPDATED", validationData };
}

const validate = debounce(
    async (processingType: string, validationRequestData: GenericValidationRequest, callback: (data: GenericValidationData) => void) => {
        const { data } = await HttpService.validateGenericActionParameters(processingType, validationRequestData);
        callback(data);
    },
    500,
);

export function validateGenericActionParameters(processingType: string, validationRequestData: GenericValidationRequest): ThunkAction {
    return (dispatch) =>
        validate(processingType, validationRequestData, (data) => {
            dispatch(nodeGenericValidationDataUpdated(data));
        });
}
