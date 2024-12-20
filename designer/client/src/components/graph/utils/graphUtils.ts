import NodeUtils from "../NodeUtils";
import { cloneDeep, isEqual, map, reject } from "lodash";
import { Edge, NodeId, NodeType, ProcessDefinitionData, ScenarioGraph } from "../../../types";
import {
    adjustBranchParametersAfterDisconnect,
    enrichNodeWithProcessDependentData,
    removeBranchParameter,
} from "../../../reducers/graph/utils";
import { dia } from "jointjs";
import { isTouchEvent } from "../../../helpers/detectDevice";

export function mapProcessWithNewNode(scenarioGraph: ScenarioGraph, before: NodeType, after: NodeType): ScenarioGraph {
    return {
        ...scenarioGraph,
        edges: map(scenarioGraph.edges, (e) => {
            if (isEqual(e.from, before.id)) {
                return { ...e, from: after.id, to: e.to };
            } else if (isEqual(e.to, before.id)) {
                return { ...e, from: e.from, to: after.id };
            } else {
                return e;
            }
        }),
        nodes: map(scenarioGraph.nodes, (n) => {
            return isEqual(n, before) ? after : mapBranchParametersWithNewNode(before.id, after.id, n);
        }),
    };
}

//we do mapping here, because we validate changed process before closing modal, and before applying state change in reducer.
function mapBranchParametersWithNewNode(beforeId: NodeId, afterId: NodeId, node: NodeType): NodeType {
    if (beforeId !== afterId && node.branchParameters?.find((bp) => bp.branchId === beforeId)) {
        const newNode = cloneDeep(node);
        const branchParameter = newNode.branchParameters.find((bp) => bp.branchId === beforeId);
        if (branchParameter) {
            branchParameter.branchId = afterId;
        }
        return newNode;
    } else {
        return node;
    }
}

export function mapProcessWithNewEdge(scenarioGraph: ScenarioGraph, before: Edge, after: Edge): ScenarioGraph {
    return {
        ...scenarioGraph,
        edges: map(scenarioGraph.edges, (e) => {
            if (isEqual(e.from, before.from) && isEqual(e.to, before.to)) {
                return after;
            } else {
                return e;
            }
        }),
    };
}

export function replaceNodeOutputEdges(
    scenarioGraph: ScenarioGraph,
    processDefinitionData: ProcessDefinitionData,
    edgesAfter: Edge[],
    nodeId: NodeType["id"],
): ScenarioGraph {
    const edgesBefore = scenarioGraph.edges.filter(({ from }) => from === nodeId);

    if (isEqual(edgesBefore, edgesAfter)) return scenarioGraph;

    const oldTargets = new Set(edgesBefore.map((e) => e.to));
    const newTargets = new Set(edgesAfter.map((e) => e.to));
    return {
        ...scenarioGraph,
        edges: scenarioGraph.edges.filter(({ from }) => !(from === nodeId)).concat(edgesAfter),
        nodes: scenarioGraph.nodes.map((node) => {
            if (newTargets.has(node.id)) {
                return enrichNodeWithProcessDependentData(node, processDefinitionData, edgesAfter);
            }
            if (oldTargets.has(node.id)) {
                return removeBranchParameter(node, nodeId);
            }
            return node;
        }),
    };
}

export function deleteNode(scenarioGraph: ScenarioGraph, nodeId: NodeId): ScenarioGraph {
    const edges = scenarioGraph.edges.filter((e) => e.from !== nodeId).map((e) => (e.to === nodeId ? { ...e, to: "" } : e));
    const nodesAfterRemove = scenarioGraph.nodes.filter((n) => n.id !== nodeId);
    const removedEdges = scenarioGraph.edges.filter((e) => e.from === nodeId);
    const nodes = adjustBranchParametersAfterDisconnect(nodesAfterRemove, removedEdges);

    return { ...scenarioGraph, edges, nodes };
}

export function canInjectNode(
    scenarioGraph: ScenarioGraph,
    sourceId: NodeId,
    middleManId: NodeId,
    targetId: NodeId,
    processDefinitionData: ProcessDefinitionData,
): boolean {
    const processAfterDisconnection = deleteEdge(scenarioGraph, sourceId, targetId);
    const canConnectSourceToMiddleMan = NodeUtils.canMakeLink(sourceId, middleManId, processAfterDisconnection, processDefinitionData);
    const processWithConnectedSourceAndMiddleMan = addEdge(processAfterDisconnection, sourceId, middleManId);
    const canConnectMiddleManToTarget = NodeUtils.canMakeLink(
        middleManId,
        targetId,
        processWithConnectedSourceAndMiddleMan,
        processDefinitionData,
    );
    return canConnectSourceToMiddleMan || canConnectMiddleManToTarget;
}

function deleteEdge(scenarioGraph: ScenarioGraph, fromId: NodeId, toId: NodeId): ScenarioGraph {
    return {
        ...scenarioGraph,
        edges: reject(scenarioGraph.edges, (e) => e.from === fromId && e.to === toId),
    };
}

function addEdge(scenarioGraph: ScenarioGraph, fromId: NodeId, toId: NodeId): ScenarioGraph {
    return {
        ...scenarioGraph,
        edges: scenarioGraph.edges.concat({ from: fromId, to: toId }),
    };
}

export const handleGraphEvent =
    <T = dia.CellView>(
        touchEvent: ((view: T, event: dia.Event) => void) | null,
        mouseEvent: ((view: T, event: dia.Event) => void) | null,
        preventDefault: (view: T) => boolean = () => true,
    ) =>
    (view: T, event: dia.Event) => {
        if (preventDefault(view)) event.preventDefault();
        return isTouchEvent(event) ? touchEvent?.(view, event) : mouseEvent?.(view, event);
    };
