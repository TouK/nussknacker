import HttpService, { SourceWithParametersTest } from "../../http/HttpService";
import { Expression, NodeValidationError, ScenarioGraph, TypingResult, VariableTypes } from "../../types";

import { debounce } from "lodash";

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

export interface TestAdhocValidationRequest {
    sourceParameters: SourceWithParametersTest;
    scenarioGraph: ScenarioGraph;
}

export const validateAdhocTestParameters = debounce(
    async (scenarioName: string, validationRequestData: TestAdhocValidationRequest, callback: (data: GenericValidationData) => void) => {
        const { data } = await HttpService.validateAdhocTestParameters(scenarioName, validationRequestData);
        callback(data);
    },
    500,
);
