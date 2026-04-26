import { debounce } from "lodash";

import { getNodesDetails } from "../../components/graph/node-modal/NodeDetailsContent/getNodeDetails";
import { parseWindowsQueryParams, replaceSearchQuery } from "../../containers/hooks/useSearchQuery";
import type { AppDispatch } from "../../store/storeHelpers";
import type { TypingResult, UIParameter } from "../../types/definition";
import type { Edge } from "../../types/edge";
import type { NodeType, PropertiesType } from "../../types/node";
import type { NodeValidationError, TestCaseValidationErrors, VariableTypes } from "../../types/validation";
import type { ThunkAction } from "../reduxTypes";
import type { Assertion, Mock } from "./testCasesActions";
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
    | NodeDetailsClosed
    | { type: "NODE_DETAILS_NEEDS_APPLY"; keys: string[] };

export interface ValidationData {
    parameters?: UIParameter[];
    expressionType?: TypingResult;
    validationErrors: NodeValidationError[];
    validationPerformed: boolean;
    testCasesValidationErrors: TestCaseValidationErrors;
}

export interface TestCaseValidationData {
    assertions: Assertion[];
    enricherMock: Mock;
}

export interface ValidationRequest {
    nodeData: NodeType;
    variableTypes: VariableTypes;
    branchVariableTypes: Record<string, VariableTypes>;
    processProperties: PropertiesType;
    outgoingEdges: Edge[];
    testCases: Record<string, TestCaseValidationData>;
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

//we don't return ThunkAction here as it would not work correctly with debounce
//TODO: use sth better, how long should be timeout?
const validateDebouncedByNode = new Map<
    string,
    ReturnType<
        typeof debounce<
            (
                dispatch: AppDispatch,
                validationRequestData: Omit<ValidationRequest, "processProperties">,
                controller: AbortController,
                callback: (data?: ValidationData | void) => void,
            ) => Promise<void>
        >
    >
>();

const nodeValidationControllers = new Map<string, AbortController>();

function getOrCreateDebouncedForNode(nodeId: string) {
    if (!validateDebouncedByNode.has(nodeId)) {
        validateDebouncedByNode.set(
            nodeId,
            debounce(async (dispatch, validationRequestData, controller, callback) => {
                const data = await dispatch(validateNode(validationRequestData, controller));
                // Only clean up if no newer controller has replaced this one.
                // If a new call aborted this request while it was in-flight, the map
                // already holds the newer controller — deleting it unconditionally would
                // prevent that controller from being aborted by subsequent calls.
                if (nodeValidationControllers.get(nodeId) === controller) {
                    nodeValidationControllers.delete(nodeId);
                }
                callback(data);
            }, 500),
        );
    }
    return validateDebouncedByNode.get(nodeId);
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
        nodeValidationControllers.get(nodeId)?.abort();
        nodeValidationControllers.delete(nodeId);
        validateDebouncedByNode.get(nodeId)?.cancel();
        validateDebouncedByNode.delete(nodeId);

        dispatch({
            type: reloadOnly ? "NODE_DETAILS_RELOAD" : "NODE_DETAILS_CLOSED",
            nodeId,
            windowId,
        });
        mergeQuery(parseWindowsQueryParams({}, { nodeId }));
    };
}

export function validateNodeData(
    validationRequestData: Omit<ValidationRequest, "processProperties">,
    callback?: (status: "allowDataUpdate" | "unknown") => void,
): ThunkAction {
    return (dispatch, getState) => {
        const nodeId = validationRequestData.nodeData.id;

        // Abort any in-flight request for this node
        nodeValidationControllers.get(nodeId)?.abort();
        const controller = new AbortController();
        nodeValidationControllers.set(nodeId, controller);

        getOrCreateDebouncedForNode(nodeId)(dispatch, validationRequestData, controller, (data) => {
            const allowDataUpdate = data && getNodesDetails(getState())[nodeId];
            callback?.(allowDataUpdate ? "allowDataUpdate" : "unknown");
        });
    };
}
