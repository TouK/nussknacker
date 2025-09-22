import { debounce } from "lodash";

import type { SourceWithParametersTest } from "../../http/HttpService";
import HttpService from "../../http/HttpService";
import type { TypingResult } from "../../types/definition";
import type { Expression } from "../../types/node";
import type { ScenarioGraph } from "../../types/scenarioGraph";
import type { NodeValidationError, VariableTypes } from "../../types/validation";

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
    testData: {
        type: string;
        sourceParameters?: SourceWithParametersTest;
    };
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
