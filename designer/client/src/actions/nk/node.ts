import { Dictionary } from "lodash";
import { flushSync } from "react-dom";
import NodeUtils from "../../components/graph/NodeUtils";
import { batchGroupBy } from "../../reducers/graph/batchGroupBy";
import { prepareNewNodesWithLayout } from "../../reducers/graph/utils";
import { getScenarioGraph } from "../../reducers/selectors/graph";
import { getProcessDefinitionData } from "../../reducers/selectors/settings";
import { Edge, EdgeType, NodeId, NodeType, ProcessDefinitionData, ValidationResult } from "../../types";
import { ThunkAction } from "../reduxTypes";
import { EditNodeAction, EditScenarioLabels } from "./editNode";
import { layoutChanged, NodePosition, Position } from "./ui/layout";

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
    nodes: NodeType[];
    layout: NodePosition[];
    idMapping: Dictionary<string>;
    edges: Edge[];
    processDefinitionData: ProcessDefinitionData;
};

type ValidationResultAction = {
    type: "VALIDATION_RESULT";
    validationResult: ValidationResult;
};

type NodeAddedAction = {
    type: "NODE_ADDED";
    nodes: NodeType[];
    layout: NodePosition[];
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
    return (dispatch, getState) => {
        batchGroupBy.startOrContinue();

        // We need to disable automatic React batching https://react.dev/blog/2022/03/29/react-v18#new-feature-automatic-batching
        // since it breaks redux undo in this case
        flushSync(() => {
            const scenarioGraph = getScenarioGraph(getState());
            const { nodes, layout } = prepareNewNodesWithLayout(scenarioGraph.nodes, [{ node, position }], false);

            dispatch({
                type: "NODE_ADDED",
                nodes,
                layout,
            });
            dispatch(layoutChanged());
        });
        batchGroupBy.end();
    };
}

export function nodesWithEdgesAdded(nodesWithPositions: NodesWithPositions, edges: Edge[]): ThunkAction {
    return (dispatch, getState) => {
        const state = getState();
        const processDefinitionData = getProcessDefinitionData(state);
        const scenarioGraph = getScenarioGraph(state);
        const { nodes, layout, idMapping } = prepareNewNodesWithLayout(scenarioGraph.nodes, nodesWithPositions, true);

        batchGroupBy.startOrContinue();
        dispatch({
            type: "NODES_WITH_EDGES_ADDED",
            nodes,
            layout,
            idMapping,
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
    | EditScenarioLabels;
