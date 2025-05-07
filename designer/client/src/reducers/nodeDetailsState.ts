import { omit } from "lodash";

import type { Action } from "../actions/reduxTypes";
import type { NodeValidationError, TypingResult, UIParameter } from "../types";

export type NodeDetailsState = Record<
    string,
    {
        parameters?: UIParameter[];
        expressionType?: TypingResult;
        validationErrors: NodeValidationError[];
        validationPerformed: boolean;
    }
>;

export function reducer(state: NodeDetailsState = {}, action: Action): NodeDetailsState {
    switch (action.type) {
        case "NODE_DETAILS_OPENED": {
            const { nodeId } = action;
            return {
                ...state,
                [nodeId]: {
                    validationErrors: [],
                    validationPerformed: false,
                },
            };
        }
        case "NODE_VALIDATION_UPDATING": {
            const { loading, nodeId } = action;
            return {
                ...state,
                [nodeId]: {
                    ...state[nodeId],
                    loading,
                },
            };
        }
        case "NODE_VALIDATION_UPDATED": {
            const { validationData, nodeId, loading } = action;
            return {
                ...state,
                [nodeId]: {
                    ...state[nodeId],
                    ...validationData,
                    loading,
                },
            };
        }
        case "NODE_DETAILS_CLOSED":
            return omit(state, action.nodeId);
        default:
            return state;
    }
}
