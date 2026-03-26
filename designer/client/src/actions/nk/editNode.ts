import type { g } from "jointjs";
import { min } from "lodash";

import { getEdgesForNode } from "../../components/graph/node-modal/node/useNodeState";
import { replaceNodeData } from "../../components/graph/node-modal/NodeSwitcherUtils";
import NodeUtils from "../../components/graph/NodeUtils";
import type { Scenario } from "../../components/Process/types";
import { batchGroupBy } from "../../reducers/graph/batchGroupBy";
import { updateAfterNodeDelete } from "../../reducers/graph/utils";
import { isFragmentCreator } from "../../reducers/selectors/appendFragmentCreator";
import { getProcessDefinitionData } from "../../reducers/selectors/getProcessDefinitionData";
import { getGraph } from "../../reducers/selectors/graph";
import type { Edge } from "../../types/edge";
import type { NodeType } from "../../types/node";
import type { ScenarioGraph } from "../../types/scenarioGraph";
import type { ValidationResult } from "../../types/validation";
import type { ThunkAction } from "../reduxTypes";
import { calculateProcessAfterChange } from "./calculateProcessAfterChange";
import { createFragment } from "./createFragment";
import { injectNode, nodesWithEdgesAdded } from "./node";
import { preApplyValidation } from "./preApplyValidation";

function replaceNode(before: NodeType, after: NodeType): ThunkAction {
    return async (dispatch, getState) => {
        batchGroupBy.startOrExtend();
        const state = getState();
        const graph = getGraph(state);
        const { scenario } = before.id === after.id ? graph : updateAfterNodeDelete(graph, after.id);
        const { nextEdges: outputEdges, nextNode } = replaceNodeData(
            before,
            after,
            getProcessDefinitionData(state),
            getEdgesForNode(scenario, before),
        );

        dispatch(editNode(scenario, before, nextNode, outputEdges));
    };
}

function addNodeWithEdgeAction(node: NodeType, position: g.PlainPoint, edge?: Edge, dryRun = false): ThunkAction<Promise<NodeType>> {
    return async (dispatch, getState) => {
        if (isFragmentCreator(node, getProcessDefinitionData(getState()))) {
            node = await dispatch(createFragment());
        }
        if (!NodeUtils.isAvailable(node, getProcessDefinitionData(getState()))) return;

        const [addedNode] = await dispatch(nodesWithEdgesAdded([{ node, position }], [edge], { isCopy: false, dryRun }));
        return addedNode;
    };
}

function calcNodesOffset(nodes: NodeType[], offset: g.PlainPoint) {
    const delta = {
        x: offset.x - min(nodes.map((node) => node.additionalFields.layoutData.x)),
        y: offset.y - min(nodes.map((node) => node.additionalFields.layoutData.y)),
    };

    return nodes.map((node) => ({
        node,
        position: {
            x: node.additionalFields.layoutData.x + delta.x,
            y: node.additionalFields.layoutData.y + delta.y,
        },
    }));
}

export type NodeAddActions =
    | { type: "EDIT_NODE"; before: NodeType; after: NodeType; validationResult?: ValidationResult; scenarioGraphAfterChange: ScenarioGraph }
    | { type: "ADD_NODE_PLAIN"; node: NodeType; offset: g.PlainPoint }
    | { type: "ADD_NODE_CONNECTED"; node: NodeType; edge: Omit<Edge, "from"> | Omit<Edge, "to">; offset: g.PlainPoint }
    | { type: "ADD_NODE_INJECT"; node: NodeType; edge: Edge; offset: g.PlainPoint }
    | { type: "ADD_NODE_REPLACE"; node: NodeType; old: NodeType }
    | { type: "ADD_NODE_MULTIPLE"; nodes: NodeType[]; edges: Edge[]; offset: g.PlainPoint }
    | { type: "MOVE_NODE_PLAIN"; node: NodeType; offset: g.PlainPoint }
    | { type: "MOVE_NODE_INJECT"; node: NodeType; edge: Edge; offset: g.PlainPoint }
    | { type: "MOVE_NODE_REPLACE"; node: NodeType; old: NodeType }
    | { type: "EDIT_LABELS"; labels: string[] };

export function editNode(
    scenarioBefore: Scenario,
    before: NodeType,
    editedNode: NodeType,
    outputEdges?: Edge[],
    controller?: AbortController,
): ThunkAction<Promise<NodeType>> {
    return async (dispatch) => {
        const scenarioGraph = await dispatch(calculateProcessAfterChange(scenarioBefore, before, editedNode, outputEdges));
        const response = await dispatch(preApplyValidation(scenarioBefore, scenarioGraph, controller));

        dispatch({
            type: "EDIT_NODE",
            before,
            after: editedNode,
            validationResult: response?.data,
            scenarioGraphAfterChange: scenarioGraph,
        });

        return editedNode;
    };
}

export function addNodePlain(node: NodeType, offset: g.PlainPoint): ThunkAction {
    return async (dispatch) => {
        await dispatch({ type: "ADD_NODE_PLAIN", node, offset });
        await dispatch(addNodeWithEdgeAction(node, offset));
    };
}

export function addNodeConnected(node: NodeType, edge: Omit<Edge, "from"> | Omit<Edge, "to">, offset: g.PlainPoint): ThunkAction {
    return async (dispatch) => {
        await dispatch({ type: "ADD_NODE_CONNECTED", node, edge, offset });
        await dispatch(addNodeWithEdgeAction(node, offset, edge as Edge));
    };
}

export function addNodeInject(node: NodeType, edge: Edge, offset: g.PlainPoint): ThunkAction {
    return async (dispatch) => {
        await dispatch({ type: "ADD_NODE_INJECT", node, edge, offset });
        const addedNode = await dispatch(addNodeWithEdgeAction(node, offset));
        await dispatch(injectNode(addedNode, edge));
    };
}

export function addNodeReplace(node: NodeType, old: NodeType): ThunkAction {
    return async (dispatch) => {
        await dispatch({ type: "ADD_NODE_REPLACE", node, old });
        const addedNode = await dispatch(addNodeWithEdgeAction(node, old.additionalFields.layoutData, null, true));
        await dispatch(replaceNode(old, addedNode));
    };
}

export function addNodeMultiple(nodes: NodeType[], edges: Edge[], offset: g.PlainPoint): ThunkAction {
    return async (dispatch) => {
        await dispatch({ type: "ADD_NODE_MULTIPLE", nodes, edges, offset });
        dispatch(nodesWithEdgesAdded(calcNodesOffset(nodes, offset), edges));
    };
}

export function moveNodePlain(node: NodeType, offset: g.PlainPoint): ThunkAction {
    return async (dispatch) => {
        await dispatch({ type: "MOVE_NODE_PLAIN", node, offset });
    };
}

export function moveNodeInject(node: NodeType, edge: Edge, offset: g.PlainPoint): ThunkAction {
    return async (dispatch) => {
        await dispatch({ type: "MOVE_NODE_INJECT", node, edge, offset });
        await dispatch(injectNode(node, edge));
    };
}

export function moveNodeReplace(node: NodeType, old: NodeType): ThunkAction {
    return async (dispatch) => {
        await dispatch({ type: "MOVE_NODE_REPLACE", node, old });
        await dispatch(replaceNode(old, node));
    };
}

export function editScenarioLabels(scenarioLabels: string[]): ThunkAction {
    return (dispatch) => {
        dispatch({ type: "EDIT_LABELS", labels: scenarioLabels });
    };
}
