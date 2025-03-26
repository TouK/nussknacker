import { debounce } from "lodash";

import type { SourceWithParametersTest } from "../../http/HttpService";
import HttpService from "../../http/HttpService";
import type { Expression, NodeValidationError, ScenarioGraph, TypingResult, VariableTypes } from "../../types";

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

export interface TestValidationRequest {
    sourceParameters: SourceWithParametersTest;
    scenarioGraph: ScenarioGraph;
}

export const validateTestParameters = debounce(
    async (
        scenarioName: string,
        sourceParameters: SourceWithParametersTest,
        scenarioGraph: ScenarioGraph,
        callback: (data: GenericValidationData) => void,
    ) => {
        const { data } = await HttpService.validateAdhocTestParameters(scenarioName, sourceParameters, scenarioGraph);
        callback(data);
    },
    500,
);
