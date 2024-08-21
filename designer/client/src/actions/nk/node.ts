import { Edge, EdgeType, NodeId, NodeType, ProcessDefinitionData, ValidationResult } from "../../types";
import { ThunkAction } from "../reduxTypes";
import { layoutChanged, Position } from "./ui/layout";
import {EditNodeAction, EditScenarioLabels, RenameProcessAction} from "./editNode";
import { getProcessDefinitionData } from "../../reducers/selectors/settings";
import { batchGroupBy } from "../../reducers/graph/batchGroupBy";
import NodeUtils from "../../components/graph/NodeUtils";
import { getScenarioGraph } from "../../reducers/selectors/graph";
import { flushSync } from "react-dom";

export type NodesWithPositions = { node: NodeType; position: Position }[];

type DeleteNodesAction = {
    type: "DELETE_NODES";
    ids: NodeId[];
};

export type NodesConnectedAction = {
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
    edges: Edge[];
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
        dispatch({
            type: "DELETE_NODES",
            ids,
        });
    };
}

export function nodesConnected(fromNode: NodeType, toNode: NodeType, edgeType?: EdgeType): ThunkAction {
    return (dispatch, getState) => {
        dispatch({
            type: "NODES_CONNECTED",
            processDefinitionData: getProcessDefinitionData(getState()),
            fromNode,
            toNode,
            edgeType,
        });
    };
}

export function nodesDisconnected(from: NodeId, to: NodeId): ThunkAction {
    return (dispatch) => {
        dispatch({
            type: "NODES_DISCONNECTED",
            from,
            to,
        });
    };
}

export function injectNode(from: NodeType, middle: NodeType, to: NodeType, { edgeType }: Edge): ThunkAction {
    return (dispatch, getState) => {
        const state = getState();
        const processDefinitionData = getProcessDefinitionData(state);
        const scenarioGraph = getScenarioGraph(state);

        batchGroupBy.startOrContinue();

        dispatch({
            type: "NODES_DISCONNECTED",
            from: from.id,
            to: to.id,
        });

        const inputs = NodeUtils.nodeInputs(middle.id, scenarioGraph);
        if (NodeUtils.canHaveMoreInputs(middle, inputs, processDefinitionData)) {
            dispatch({
                type: "NODES_CONNECTED",
                fromNode: from,
                toNode: middle,
                processDefinitionData,
                edgeType,
            });
        }

        const outputs = NodeUtils.nodeOutputs(middle.id, scenarioGraph);
        if (NodeUtils.canHaveMoreOutputs(middle, outputs, processDefinitionData)) {
            dispatch({
                type: "NODES_CONNECTED",
                fromNode: middle,
                toNode: to,
                processDefinitionData,
            });
        }
        dispatch(layoutChanged());
        batchGroupBy.end();
    };
}

export function nodeAdded(node: NodeType, position: Position): ThunkAction {
    return (dispatch) => {
        batchGroupBy.startOrContinue();

        // We need to disable automatic React batching https://react.dev/blog/2022/03/29/react-v18#new-feature-automatic-batching
        // since it breaks redux undo in this case
        flushSync(() => {
            dispatch({ type: "NODE_ADDED", node, position });
            dispatch(layoutChanged());
        });
        batchGroupBy.end();
    };
}

export function nodesWithEdgesAdded(nodesWithPositions: NodesWithPositions, edges: Edge[]): ThunkAction {
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
    | RenameProcessAction
    | EditScenarioLabels;
