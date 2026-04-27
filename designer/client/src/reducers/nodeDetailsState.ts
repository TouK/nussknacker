import { v4 as uuid4 } from "uuid";

import type { Action } from "../actions/reduxTypes";
import type { TypingResult, UIParameter } from "../types/definition";
import type { NodeId } from "../types/node";
import type { NodeValidationError, TestCaseValidationErrors } from "../types/validation";

export type NodeDetailsState = Partial<
    Record<
        ".properties" | NodeId,
        {
            parameters: UIParameter[];
            expressionType?: TypingResult;
            validationErrors: NodeValidationError[];
            validationPerformed: boolean;
            changingDynamicParameters: string[];
            testCasesValidationErrors: TestCaseValidationErrors;
        }
    >
>;

export function reducer(state: NodeDetailsState = {}, action: Action): NodeDetailsState {
    switch (action.type) {
        case "NODE_DETAILS_OPENED": {
            const { nodeId } = action;
            return {
                ...state,
                [nodeId]: {
                    parameters: [],
                    validationErrors: [],
                    validationPerformed: false,
                    changingDynamicParameters: [],
                },
            };
        }

        case "NODE_VALIDATION_DYNAMIC_PARAMETERS_LOADING": {
            const { nodeId, dynamicParametersChanged } = action;
            return {
                ...state,
                [nodeId]: {
                    ...state[nodeId],
                    changingDynamicParameters: dynamicParametersChanged,
                },
            };
        }

        case "NODE_VALIDATION_DYNAMIC_PARAMETERS_LOADED": {
            const { nodeId } = action;
            return {
                ...state,
                [nodeId]: {
                    ...state[nodeId],
                    changingDynamicParameters: [],
                },
            };
        }

        case "NODE_VALIDATION_UPDATED": {
            const { validationData, nodeId } = action;
            const requestId = uuid4();
            return {
                ...state,
                [nodeId]: {
                    ...state[nodeId],
                    ...validationData,
                    validationErrors: validationData.validationErrors.map((e) => ({ ...e, requestId })),
                },
            };
        }

        case "PROPERTIES_VALIDATION_UPDATED": {
            return {
                ...state,
                ".properties": {
                    parameters: [],
                    expressionType: null,
                    validationErrors: action.errors,
                    validationPerformed: true,
                    changingDynamicParameters: [],
                    testCasesValidationErrors: {},
                },
            };
        }

        case "CLEAR_PROCESS":
            return {};
        default:
            return state;
    }
}
