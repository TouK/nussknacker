import { Action } from "../actions/reduxTypes";
import { NodeValidationError, TypingResult, UIParameter } from "../types";
import { omit } from "lodash";

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
        case "NODE_VALIDATION_UPDATED": {
            const { validationData, nodeId } = action;
            return {
                ...state,
                [nodeId]: {
                    ...state[nodeId],
                    validationErrors: validationData.validationErrors,
                    parameters: validationData.parameters,
                    expressionType: validationData.expressionType,
                    validationPerformed: validationData.validationPerformed,
                },
            };
        }
        case "NODE_DETAILS_CLOSED":
            return omit(state, action.nodeId);
        default:
            return state;
    }
}
