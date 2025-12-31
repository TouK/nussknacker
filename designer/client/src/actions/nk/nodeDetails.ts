import { debounce } from "lodash";

import { getNodeDetails } from "../../components/graph/node-modal/NodeDetailsContent/getNodeDetails";
import { parseWindowsQueryParams, replaceSearchQuery } from "../../containers/hooks/useSearchQuery";
import type { AppDispatch } from "../../store/storeHelpers";
import type { TypingResult, UIParameter } from "../../types/definition";
import type { Edge } from "../../types/edge";
import type { NodeType, PropertiesType } from "../../types/node";
import type { NodeValidationError, VariableTypes } from "../../types/validation";
import type { ThunkAction } from "../reduxTypes";
import { validateNode } from "./validationsActions";

type NodeValidationUpdated = { type: "NODE_VALIDATION_UPDATED"; validationData: ValidationData; nodeId: string };
type NodeDetailsOpened = { type: "NODE_DETAILS_OPENED"; nodeId: string; windowId: string };
type NodeDetailsClosed = { type: "NODE_DETAILS_RELOAD" | "NODE_DETAILS_CLOSED"; nodeId: string; windowId: string };
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

function mergeQuery(changes: Record<string, string[]>) {
    return replaceSearchQuery((current) => ({ ...current, ...changes }));
}

export function nodeDetailsOpened(nodeId: string, windowId: string): ThunkAction {
    return (dispatch) => {
        dispatch({
            type: "NODE_DETAILS_OPENED",
            nodeId,
            windowId,
        });
        mergeQuery(parseWindowsQueryParams({ nodeId: nodeId }));
    };
}

export function nodeDetailsClosed(nodeId: string, windowId: string, reloadOnly?: boolean): ThunkAction {
    return (dispatch) => {
        dispatch({
            type: reloadOnly ? "NODE_DETAILS_RELOAD" : "NODE_DETAILS_CLOSED",
            nodeId,
            windowId,
        });
        mergeQuery(parseWindowsQueryParams({}, { nodeId }));
    };
}

//we don't return ThunkAction here as it would not work correctly with debounce
//TODO: use sth better, how long should be timeout?
const validateDebounced = debounce(
    async (
        dispatch: AppDispatch,
        validationRequestData: Omit<ValidationRequest, "processProperties">,
        callback: (data?: ValidationData | void) => void,
    ) => {
        const data = await dispatch(validateNode(validationRequestData));
        callback(data);
    },
    500,
);

export function validateNodeData(
    validationRequestData: Omit<ValidationRequest, "processProperties">,
    callback?: (status: "allowDataUpdate" | "unknown") => void,
): ThunkAction {
    return (dispatch, getState) => {
        validateDebounced(dispatch, validationRequestData, (data) => {
            const allowDataUpdate = data && getNodeDetails(getState())(validationRequestData.nodeData.id);
            callback?.(allowDataUpdate ? "allowDataUpdate" : "unknown");
        });
    };
}
