import type { dia } from "jointjs";
import type { Dictionary } from "lodash";
import { flushSync } from "react-dom";

import NodeUtils from "../../components/graph/NodeUtils";
import { canInjectNode } from "../../components/graph/utils/graphUtils";
import { batchGroupBy } from "../../reducers/graph/batchGroupBy";
import { prepareNewNodesWithLayout } from "../../reducers/graph/utils";
import { getProcessDefinitionData } from "../../reducers/selectors/getProcessDefinitionData";
import { getNodes, getScenarioGraph } from "../../reducers/selectors/graph";
import type { Edge, EdgeType } from "../../types/edge";
import type { Dimensions, NodeId, NodeType } from "../../types/node";
import type { ProcessDefinitionData } from "../../types/scenarioGraph";
import type { NodeValidationError, ValidationResult } from "../../types/validation";
import type { ThunkAction } from "../reduxTypes";
import type { NodeAddActions } from "./editNode";
import type { NodePosition, Position } from "./ui/layout";
import { layoutChanged } from "./ui/layout";

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

type StickyNoteUpdatedAction = {
    type: "STICKY_NOTE_UPDATED";
    id: string;
    dimensions: Dimensions;
    content?: string;
};

type StickyNoteSetErrorsAction = {
    type: "STICKY_NOTE_SET_ERRORS";
    stickyNoteErrors: Record<string, NodeValidationError[] | null>;
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

export function injectNode(node: NodeType, edge: Edge): ThunkAction {
    return async (dispatch, getState) => {
        const state = getState();
        let scenarioGraph = getScenarioGraph(state);
        const processDefinitionData = getProcessDefinitionData(state);

        if (!canInjectNode(scenarioGraph, edge.from, node.id, edge.to, processDefinitionData)) return;

        const from = NodeUtils.getNodeById(edge.from, scenarioGraph);
        const to = NodeUtils.getNodeById(edge.to, scenarioGraph);

        dispatch({
            type: "NODES_DISCONNECTED",
            from: edge.from,
            to: edge.to,
        });

        scenarioGraph = getScenarioGraph(getState());
        const inputs = NodeUtils.nodeInputs(node.id, scenarioGraph);
        if (from && NodeUtils.canHaveMoreInputs(node, inputs, processDefinitionData)) {
            dispatch({
                type: "NODES_CONNECTED",
                fromNode: from,
                toNode: node,
                processDefinitionData,
                edgeType: edge.edgeType,
            });
        }

        const outputs = NodeUtils.nodeOutputs(node.id, scenarioGraph);
        if (to && NodeUtils.canHaveMoreOutputs(node, outputs, processDefinitionData)) {
            dispatch({
                type: "NODES_CONNECTED",
                fromNode: node,
                toNode: to,
                processDefinitionData,
            });
        }
        dispatch(layoutChanged());
    };
}

export function nodeAdded(node: NodeType, position: Position): ThunkAction {
    return (dispatch, getState) => {
        batchGroupBy.startOrExtend();

        // We need to disable automatic React batching https://react.dev/blog/2022/03/29/react-v18#new-feature-automatic-batching
        // since it breaks redux undo in this case
        flushSync(() => {
            const currentNodes = getNodes(getState());
            const { nodes, layout } = prepareNewNodesWithLayout(currentNodes, [{ node, position }], false);

            dispatch({
                type: "NODE_ADDED",
                nodes,
                layout,
            });
            dispatch(layoutChanged());
        });
    };
}

export function stickyNoteUpdated(element: dia.Element, content?: string): ThunkAction {
    return (dispatch) => {
        batchGroupBy.startOrExtend();
        const id = element.id.toString();
        const dimensions = element.attributes.size as Dimensions;
        flushSync(() => {
            dispatch({
                type: "STICKY_NOTE_UPDATED",
                id,
                dimensions,
                content,
            });
            dispatch(layoutChanged());
        });
    };
}

export function stickyNoteSetErrors(stickyNoteErrors: Record<string, NodeValidationError[] | null>): ThunkAction {
    return (dispatch) => {
        dispatch({
            type: "STICKY_NOTE_SET_ERRORS",
            stickyNoteErrors,
        });
    };
}

type Options = { isCopy?: boolean; dryRun?: boolean };
export function nodesWithEdgesAdded(
    nodesWithPositions: NodesWithPositions,
    edges: Edge[],
    { isCopy = true, dryRun = false }: Options = {},
): ThunkAction<Promise<NodeType[]>> {
    return async (dispatch, getState) => {
        const state = getState();
        const processDefinitionData = getProcessDefinitionData(state);
        const scenarioGraph = getScenarioGraph(state);
        const { nodes, layout, idMapping } = prepareNewNodesWithLayout(scenarioGraph.nodes, nodesWithPositions, isCopy);

        if (dryRun) return nodes;

        batchGroupBy.startOrExtend();
        await dispatch({
            type: "NODES_WITH_EDGES_ADDED",
            nodes,
            layout,
            idMapping,
            edges: edges.filter(Boolean),
            processDefinitionData,
        });
        dispatch(layoutChanged());
        return nodes;
    };
}

export type NodeActions =
    | NodeAddedAction
    | StickyNoteUpdatedAction
    | StickyNoteSetErrorsAction
    | DeleteNodesAction
    | NodesConnectedAction
    | NodesDisonnectedAction
    | NodesWithEdgesAddedAction
    | ValidationResultAction
    | NodeAddActions;
