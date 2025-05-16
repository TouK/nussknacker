import { debounce } from "lodash";

import { applyIdFromFakeName } from "../../components/graph/node-modal/IdField";
import { getNodeDetails } from "../../components/graph/node-modal/NodeDetailsContent/selectors";
import HttpService from "../../http/HttpService";
import type { Edge, NodeId, NodeType, NodeValidationError, PropertiesType, TypingResult, UIParameter, VariableTypes } from "../../types";
import type { ThunkAction } from "../reduxTypes";

type NodeValidationUpdated = { type: "NODE_VALIDATION_UPDATED"; validationData: ValidationData; nodeId: string };
type NodeDetailsOpened = { type: "NODE_DETAILS_OPENED"; nodeId: string; windowId: string };
type NodeDetailsClosed = { type: "NODE_DETAILS_CLOSED"; nodeId: string; windowId: string };
type NodeValidationDynamicParametersLoading = {
    type: "NODE_VALIDATION_DYNAMIC_PARAMETERS_LOADING";
    nodeId: string;
    dynamicParametersChanged: string[];
};
type NodeValidationDynamicParametersLoaded = {
    type: "NODE_VALIDATION_DYNAMIC_PARAMETERS_LOADED";
    nodeId: string;
};
export type NodeDetailsActions =
    | NodeValidationUpdated
    | NodeDetailsOpened
    | NodeValidationDynamicParametersLoading
    | NodeValidationDynamicParametersLoaded
    | NodeDetailsClosed;

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

export function nodeValidationDynamicParametersLoading(
    nodeId: string,
    dynamicParametersChanged: string[],
): NodeValidationDynamicParametersLoading {
    return {
        type: "NODE_VALIDATION_DYNAMIC_PARAMETERS_LOADING",
        nodeId,
        dynamicParametersChanged,
    };
}

export function nodeValidationDynamicParametersLoaded(nodeId: string): NodeValidationDynamicParametersLoaded {
    return {
        type: "NODE_VALIDATION_DYNAMIC_PARAMETERS_LOADED",
        nodeId,
    };
}

export function nodeDetailsOpened(nodeId: string, windowId: string): NodeDetailsOpened {
    return {
        type: "NODE_DETAILS_OPENED",
        nodeId,
        windowId,
    };
}

export function nodeDetailsClosed(nodeId: string, windowId: string): NodeDetailsClosed {
    return {
        type: "NODE_DETAILS_CLOSED",
        nodeId,
        windowId,
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

export function validateNodeData(
    processName: string,
    validationRequestData: ValidationRequest,
    callback?: ({ status }: { status: "allowDataUpdate" | "unknown" }) => void,
): ThunkAction {
    return (dispatch, getState) => {
        validate(processName, validationRequestData, (nodeId, data) => {
            const allowDataUpdate = data && getNodeDetails(getState())(nodeId);
            // node details view creates this on open and removes after close
            if (allowDataUpdate) {
                dispatch(nodeValidationDataUpdated(nodeId, data));
            }

            callback?.({ status: allowDataUpdate ? "allowDataUpdate" : "unknown" });
        });
    };
}
