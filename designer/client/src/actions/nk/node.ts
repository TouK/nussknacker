import { Edge, EdgeType, NodeId, NodeType, ProcessDefinitionData, ValidationResult } from "../../types";
import { ThunkAction } from "../reduxTypes";
import { layoutChanged, Position } from "./ui/layout";
import { EditNodeAction, RenameProcessAction } from "./editNode";
import { getProcessDefinitionData } from "../../reducers/selectors/settings";
import { batchGroupBy } from "../../reducers/graph/batchGroupBy";

//TODO: identify
type Edges = $TodoType[];

export type NodesWithPositions = { node: NodeType; position: Position }[];

type DeleteNodesAction = {
    type: "DELETE_NODES";
    ids: NodeId[];
};

type NodesConnectedAction = {
    type: "NODES_CONNECTED";
    fromNode: NodeType;
    toNode: NodeType;
    processDefinitionData: ProcessDefinitionData;
    edgeType?: EdgeType;
};

type NodesDisonnectedAction = {
    type: "NODES_DISCONNECTED";
    from: NodeId;
    to: NodeId;
};

type NodesWithEdgesAddedAction = {
    type: "NODES_WITH_EDGES_ADDED";
    nodesWithPositions: NodesWithPositions;
    edges: Edges;
    processDefinitionData: ProcessDefinitionData;
};

type ValidationResultAction = {
    type: "VALIDATION_RESULT";
    validationResult: ValidationResult;
};

type NodeAddedAction = {
    type: "NODE_ADDED";
    node: NodeType;
    position: Position;
};

export function deleteNodes(ids: NodeId[]): ThunkAction {
    return (dispatch) => {
        batchGroupBy.startOrContinue();
        dispatch({
            type: "DELETE_NODES",
            ids: ids,
        });
    };
}

export function nodesConnected(fromNode: NodeType, toNode: NodeType, edgeType?: EdgeType): ThunkAction {
    return (dispatch, getState) => {
        batchGroupBy.startOrContinue();
        dispatch({
            type: "NODES_CONNECTED",
            processDefinitionData: getState().settings.processDefinitionData,
            fromNode,
            toNode,
            edgeType,
        });
    };
}

export function nodesDisconnected(from: NodeId, to: NodeId): ThunkAction {
    return (dispatch) => {
        batchGroupBy.startOrContinue();
        dispatch({
            type: "NODES_DISCONNECTED",
            from,
            to,
        });
    };
}

export function injectNode(from: NodeType, middle: NodeType, to: NodeType, { edgeType }: Edge): ThunkAction {
    return (dispatch, getState) => {
        const processDefinitionData = getProcessDefinitionData(getState());
        batchGroupBy.startOrContinue();
        dispatch({
            type: "NODES_DISCONNECTED",
            from: from.id,
            to: to.id,
        });
        dispatch({
            type: "NODES_CONNECTED",
            fromNode: from,
            toNode: middle,
            processDefinitionData,
            edgeType,
        });
        dispatch({
            type: "NODES_CONNECTED",
            fromNode: middle,
            toNode: to,
            processDefinitionData,
        });
    };
}

export function nodeAdded(node: NodeType, position: Position): ThunkAction {
    return (dispatch) => {
        batchGroupBy.startOrContinue();
        dispatch({ type: "NODE_ADDED", node, position });
        dispatch(layoutChanged());
        batchGroupBy.end();
    };
}

export function nodesWithEdgesAdded(nodesWithPositions: NodesWithPositions, edges: Edges): ThunkAction {
    return (dispatch, getState) => {
        const processDefinitionData = getProcessDefinitionData(getState());
        batchGroupBy.startOrContinue();
        dispatch({
            type: "NODES_WITH_EDGES_ADDED",
            nodesWithPositions,
            edges,
            processDefinitionData,
        });
        dispatch(layoutChanged());
        batchGroupBy.end();
    };
}

export type NodeActions =
    | NodeAddedAction
    | DeleteNodesAction
    | NodesConnectedAction
    | NodesDisonnectedAction
    | NodesWithEdgesAddedAction
    | ValidationResultAction
    | EditNodeAction
    | RenameProcessAction;
