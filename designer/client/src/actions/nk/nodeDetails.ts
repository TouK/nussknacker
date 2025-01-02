import { ThunkAction } from "../reduxTypes";
import HttpService from "../../http/HttpService";
import { Edge, NodeId, NodeType, NodeValidationError, PropertiesType, TypingResult, UIParameter, VariableTypes } from "../../types";

import { debounce } from "lodash";
import NodeUtils from "../../components/graph/NodeUtils";
import { applyIdFromFakeName } from "../../components/graph/node-modal/IdField";

type NodeValidationUpdated = { type: "NODE_VALIDATION_UPDATED"; validationData: ValidationData; nodeId: string };
type NodeDetailsOpened = { type: "NODE_DETAILS_OPENED"; nodeId: string };
type NodeDetailsClosed = { type: "NODE_DETAILS_CLOSED"; nodeId: string };

export type NodeDetailsActions = NodeValidationUpdated | NodeDetailsOpened | NodeDetailsClosed;

export interface ValidationData {
    parameters?: UIParameter[];
    expressionType?: TypingResult;
    validationErrors: NodeValidationError[];
    validationPerformed: boolean;
}

export interface ValidationRequest {
    nodeData: NodeType;
    variableTypes: VariableTypes;
    branchVariableTypes: Record<string, VariableTypes>;
    processProperties: PropertiesType;
    outgoingEdges: Edge[];
}

export function nodeValidationDataUpdated(nodeId: string, validationData: ValidationData): NodeValidationUpdated {
    return {
        type: "NODE_VALIDATION_UPDATED",
        validationData,
        nodeId,
    };
}

export function nodeDetailsOpened(nodeId: string): NodeDetailsOpened {
    return {
        type: "NODE_DETAILS_OPENED",
        nodeId,
    };
}

export function nodeDetailsClosed(nodeId: string): NodeDetailsClosed {
    return {
        type: "NODE_DETAILS_CLOSED",
        nodeId,
    };
}

//we don't return ThunkAction here as it would not work correctly with debounce
//TODO: use sth better, how long should be timeout?
const validate = debounce(
    async (
        processName: string,
        validationRequestData: ValidationRequest,
        callback: (nodeId: NodeId, data?: ValidationData | void) => void,
    ) => {
        const validate = (node: NodeType) => HttpService.validateNode(processName, { ...validationRequestData, nodeData: node });

        const nodeId = validationRequestData.nodeData.id;
        const nodeWithChangedName = applyIdFromFakeName(validationRequestData.nodeData);
        const data = await validate(nodeWithChangedName);
        callback(nodeId, data);
    },
    500,
);

export function validateNodeData(processName: string, validationRequestData: ValidationRequest): ThunkAction {
    return (dispatch, getState) => {
        validate(processName, validationRequestData, (nodeId, data) => {
            // node details view creates this on open and removes after close
            if (data && getState().nodeDetails[nodeId]) {
                dispatch(nodeValidationDataUpdated(nodeId, data));
            }
        });
    };
}
