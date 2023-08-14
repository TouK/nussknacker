import { ThunkAction } from "../reduxTypes";
import HttpService from "../../http/HttpService";
import { Edge, NodeId, NodeType, NodeValidationError, PropertiesType, TypingResult, UIParameter, VariableTypes } from "../../types";

import { debounce } from "lodash";
import NodeUtils from "../../components/graph/NodeUtils";

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
    async (processId: string, validationRequestData: ValidationRequest, callback: (data: ValidationData, nodeId: NodeId) => void) => {
        const nodeId = validationRequestData.nodeData.id;
        if (NodeUtils.nodeIsProperties(validationRequestData.nodeData)) {
            //NOTE: we don't validationRequestData contains processProperties, but they are refreshed only on modal open
            const { data } = await HttpService.validateProperties(processId, validationRequestData.nodeData);
            callback(data, nodeId);
        } else {
            const { data } = await HttpService.validateNode(processId, validationRequestData);
            callback(data, nodeId);
        }
    },
    500,
);

export function validateNodeData(processId: string, validationRequestData: ValidationRequest): ThunkAction {
    return (dispatch, getState) => {
        validate(processId, validationRequestData, (data, nodeId) => {
            // node details view creates this on open and removes after close
            if (getState().nodeDetails[nodeId]) {
                dispatch(nodeValidationDataUpdated(nodeId, data));
            }
        });
    };
}
