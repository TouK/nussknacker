import HttpService from "../../http/HttpService";
import { Expression, NodeValidationError, TypingResult, VariableTypes } from "../../types";

import { debounce } from "lodash";
import { TestAdhocValidationRequest } from "./testAdhoc";

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

export const validateGenericActionParameters = debounce(
    async (scenarioName: string, validationRequestData: TestAdhocValidationRequest, callback: (data: GenericValidationData) => void) => {
        const { data } = await HttpService.validateAdhocTestParameters(scenarioName, validationRequestData);
        callback(data);
    },
    500,
);
