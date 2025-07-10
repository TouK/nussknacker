import { omit } from "lodash";

import type { Action } from "../actions/reduxTypes";
import type { NodeValidationError, TypingResult, UIParameter } from "../types";

export type NodeDetailsState = Record<
    string,
    {
        parameters: UIParameter[];
        expressionType?: TypingResult;
        validationErrors: NodeValidationError[];
        validationPerformed: boolean;
        changingDynamicParameters: string[];
    }
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
            return {
                ...state,
                [nodeId]: {
                    ...state[nodeId],
                    ...validationData,
                },
            };
        }

        default:
            return state;
    }
}
