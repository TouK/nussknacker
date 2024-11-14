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
