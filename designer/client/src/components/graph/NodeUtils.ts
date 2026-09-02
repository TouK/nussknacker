/* eslint-disable i18next/no-literal-string */
import { isEqual, uniqBy } from "lodash";

import { determineComponentId } from "../../common/componentUtils";
import ProcessUtils from "../../common/ProcessUtils";
import { memoizeByArgsWithTTL } from "../../helpers/memoizeByArgsWithTTL";
import { createEdge } from "../../reducers/graph/utils";
import type { AvailableEdgeType, Edge, EdgeType } from "../../types/edge";
import { EdgeKind } from "../../types/edge";
import type { FragmentNodeType, NodeId, NodeType } from "../../types/node";
import type { ProcessDefinitionData, ScenarioGraph } from "../../types/scenarioGraph";
import { NoWrap, WrapAllMethods } from "../../WrapAllMethods";

@WrapAllMethods(memoizeByArgsWithTTL)
class NodeUtils {
    @NoWrap
    nodeIsFragment = (node: NodeType): node is FragmentNodeType => {
        return node.type === "FragmentInput";
    };

    @NoWrap
    nodeIsJoin = (node: NodeType): boolean => {
        return node?.type === "Join";
    };

    nodesFromScenarioGraph = (scenarioGraph?: ScenarioGraph): NodeType[] => scenarioGraph?.nodes || [];

    edgesFromScenarioGraph = (scenarioGraph: ScenarioGraph) => scenarioGraph.edges || [];

    getNodeById = (nodeId: NodeId, scenarioGraph?: ScenarioGraph) =>
        this.nodesFromScenarioGraph(scenarioGraph).find((n) => n.id === nodeId);

    getNodeByName = (name: string, scenarioGraph: ScenarioGraph) => this.nodesFromScenarioGraph(scenarioGraph).find((n) => n.name === name);

    getEdgeById = (edgeId: NodeId, scenarioGraph: ScenarioGraph) =>
        this.edgesFromScenarioGraph(scenarioGraph).find((e) => this.edgeId(e) === edgeId);

    getAllNodesById = (nodeIds: NodeId[], scenarioGraph: ScenarioGraph) => {
        const allNodes = this.nodesFromScenarioGraph(scenarioGraph).filter((node) => nodeIds.includes(node.id));
        return uniqBy(allNodes, (n) => n.id);
    };

    getAllNodesByIdWithEdges = (ids: NodeId[], scenarioGraph: ScenarioGraph) => {
        const nodes = this.getAllNodesById(ids, scenarioGraph);
        const edgesForNodes = this.getEdgesForConnectedNodes(
            nodes.map((n) => n.id),
            scenarioGraph,
        );
        return {
            nodes: nodes,
            edges: edgesForNodes,
        };
    };

    isAvailable = (node: NodeType, processDefinitionData: ProcessDefinitionData): boolean => {
        return ProcessUtils.extractComponentDefinition(node, processDefinitionData.components) != null;
    };

    getOutputEdges = (nodeId: NodeId, edges: Edge[]): Edge[] => edges.filter((e) => e.from === nodeId);

    getInputEdges = (nodeId: NodeId, edges: Edge[]): Edge[] => edges.filter((e) => e.to === nodeId);

    getEdgesForConnectedNodes = (nodeIds: NodeId[], scenarioGraph: ScenarioGraph): Edge[] =>
        scenarioGraph.edges?.filter((edge) => nodeIds.includes(edge.from) && nodeIds.includes(edge.to));

    getNextEdgeType = (allEdges: Edge[], node: NodeType, processDefinitionData: ProcessDefinitionData): AvailableEdgeType => {
        const edgesForNode = this.getEdgesAvailableForNode(node, processDefinitionData);

        if (edgesForNode.canChooseNodes) {
            return edgesForNode.edges[0];
        } else {
            const currentNodeEdges = allEdges.filter((edge) => edge.from === node.id);
            const currentEdgeTypes = currentNodeEdges.map((e) => e.edgeType);
            return edgesForNode.edges.find((et) => !currentEdgeTypes.some((currentType) => isEqual(currentType, et)));
        }
    };

    getEdgesAvailableForNode = (
        node: NodeType,
        processDefinitionData: ProcessDefinitionData,
        forInput = false,
    ): { edges: AvailableEdgeType[]; canChooseNodes: boolean } => {
        const componentId = determineComponentId(node);
        //TODO: when we add more configuration for joins, probably more complex logic will be needed
        const edgesForNode = processDefinitionData.edgesForNodes
            .filter((e) => !forInput || e.isForInputDefinition === forInput)
            .find((e) => e.componentId === componentId);
        // A node with no entry connects through untyped edges - the single `undefined` slot stands for them.
        return edgesForNode || { edges: [undefined], canChooseNodes: false };
    };

    edgeLabel = (edge: Edge) => {
        const edgeType = edge?.edgeType;
        switch (edgeType?.type) {
            case EdgeKind.fragmentOutput:
            case EdgeKind.customNodeOutput:
                return edgeType?.name;
            case EdgeKind.switchNext:
                return edgeType?.condition?.expression;
        }
        return this.edgeTypeLabel(edgeType?.type);
    };

    //TODO: i18next
    edgeTypeLabel = (type: string) => {
        switch (type) {
            case EdgeKind.filterFalse:
                return "🔴 false";
            case EdgeKind.filterTrue:
                return "🟢 true";
            case EdgeKind.switchDefault:
                return "default (deprecated)";
            case EdgeKind.switchNext:
                return "condition";
            default:
                return "";
        }
    };

    //we don't allow multi outputs other than split, filter, switch and no multiple inputs
    //TODO remove type (Source, Sink) comparisons
    canMakeLink = (
        fromId: NodeId,
        toId: NodeId,
        scenarioGraph: ScenarioGraph,
        processDefinitionData: ProcessDefinitionData,
        previousEdge?: Edge,
    ): boolean => {
        const nodeInputs = this.nodeInputs(toId, scenarioGraph);
        //we do not want to include currently edited edge
        const nodeOutputs = this.nodeOutputs(fromId, scenarioGraph).filter(
            (e) => e.from !== previousEdge?.from && e.to !== previousEdge?.to,
        );

        const to = this.getNodeById(toId, scenarioGraph);
        const from = this.getNodeById(fromId, scenarioGraph);
        if (fromId !== toId) {
            const canHaveMoreInputs = this.canHaveMoreInputs(to, nodeInputs, processDefinitionData);
            const canHaveMoreOutputs = this.canHaveMoreOutputs(from, nodeOutputs, processDefinitionData);
            const isUniqe = !nodeInputs.find((e) => e.from === fromId);
            return canHaveMoreInputs && canHaveMoreOutputs && isUniqe;
        }

        return false;
    };

    getInputsMaxCount = (node: NodeType, processDefinitionData: ProcessDefinitionData): number => {
        const edgesForNode = this.getEdgesAvailableForNode(node, processDefinitionData, true);
        const maxEdgesForNode = edgesForNode.canChooseNodes ? Infinity : edgesForNode.edges.length;
        return this.hasInputs(node) ? maxEdgesForNode : 0;
    };

    getOutputsMaxCount = (node: NodeType, processDefinitionData: ProcessDefinitionData): number => {
        const edgesForNode = this.getEdgesAvailableForNode(node, processDefinitionData, false);
        const maxEdgesForNode = edgesForNode.canChooseNodes ? Infinity : edgesForNode.edges.length;
        return this.hasOutputs(node, processDefinitionData) ? maxEdgesForNode : 0;
    };

    canHaveMoreInputs = (node: NodeType, nodeInputs: Edge[], processDefinitionData: ProcessDefinitionData): boolean => {
        return nodeInputs.length < this.getInputsMaxCount(node, processDefinitionData);
    };

    canHaveMoreOutputs = (node: NodeType, nodeOutputs: Edge[], processDefinitionData: ProcessDefinitionData): boolean => {
        const maxEdgesForNode = this.getOutputsMaxCount(node, processDefinitionData);
        return nodeOutputs.filter((e) => e.to).length < maxEdgesForNode;
    };

    getFirstUnconnectedOutputEdge = (currentEdges: Edge[], availableEdges: AvailableEdgeType[], edgeType?: EdgeType) => {
        const freeOutputEdges = currentEdges
            .filter((e) => !e.to)
            //we do this to skip e.g. edges that became incorrect/unavailable
            .filter((e) =>
                availableEdges.find((available) => available?.name == e?.edgeType?.name && available?.type == e?.edgeType?.type),
            );

        return freeOutputEdges.find((e) => isEqual(e.edgeType, edgeType)) || (edgeType ? undefined : freeOutputEdges[0]);
    };

    getEdgeForConnection = ({
        fromNode,
        toNode,
        edgeType,
        scenarioGraph,
        processDefinition,
    }: {
        fromNode: NodeType;
        toNode: NodeType;
        edgeType?: EdgeType;
        scenarioGraph: ScenarioGraph;
        processDefinition: ProcessDefinitionData;
    }): Edge => {
        const { edges: availableNodeEdges } = this.getEdgesAvailableForNode(fromNode, processDefinition);
        const currentNodeEdges = this.getOutputEdges(fromNode?.id, this.edgesFromScenarioGraph(scenarioGraph));
        const freeOutputEdge = this.getFirstUnconnectedOutputEdge(currentNodeEdges, availableNodeEdges, edgeType);
        return freeOutputEdge || createEdge(fromNode, toNode, edgeType, currentNodeEdges, processDefinition);
    };

    nodeInputs = (nodeId: NodeId, scenarioGraph: ScenarioGraph) => {
        return this.edgesFromScenarioGraph(scenarioGraph).filter((e) => e.to == nodeId);
    };

    nodeOutputs = (nodeId: NodeId, scenarioGraph: ScenarioGraph) => {
        return this.getOutputEdges(nodeId, this.edgesFromScenarioGraph(scenarioGraph));
    };

    edgeId = (edge: Edge): string => {
        return `${edge.from}-${edge.to}`;
    };

    noInputNodeTypes = ["Source", "FragmentInputDefinition", "StickyNoteNode"];

    noOutputNodeTypes = ["Sink", "FragmentOutputDefinition", "StickyNoteNode"];

    //TODO: methods below should be based on backend data, e.g. Fragment can have outputs or not - based on individual fragment...
    hasInputs = (node: NodeType): boolean => {
        return !this.noInputNodeTypes.includes(node?.type);
    };

    hasOutputs = (node: NodeType, processDefinitionData?: ProcessDefinitionData): boolean => {
        switch (node?.type) {
            case "FragmentInput": {
                const fragmentComponentId = determineComponentId(node);
                const edgesFromDefinition = processDefinitionData?.edgesForNodes.find(
                    (e) => e.componentId === fragmentComponentId && !e.isForInputDefinition,
                )?.edges;
                // Is this fallback is ok? This function is used for correctFetchedDetails. Why we prefer to
                // clean edges when referred fragment lost all outputs but don't clean them when fragment was removed at all?
                return (edgesFromDefinition || Object.keys(node.ref.outputVariableNames)).length > 0;
            }
            // Can't we use processDefinitionData?.edgesForNodes for every node type?
            default: {
                return !this.noOutputNodeTypes.includes(node?.type);
            }
        }
    };

    // We can recognize that a node is part of a referenced fragment by making sure that:
    // 1. The nodeId is not present in the scenarioGraph
    // 2. There is a fragment node in the scenarioGraph whose name is part of the nodeId
    isFragmentNodeReference = (nodeId: NodeId, scenarioGraph: ScenarioGraph): boolean => {
        const isNodePresentInScenarioGraph = !!this.getNodeById(nodeId, scenarioGraph);
        return !isNodePresentInScenarioGraph && scenarioGraph.nodes.some((n) => nodeId.startsWith(n.id));
    };

    getDetailsFromFragmentNode = (
        nodeId: NodeId,
        scenarioGraph: ScenarioGraph,
    ): { fragmentId: string; fragmentNodeId: string; fragmentName: string } | null => {
        if (this.isFragmentNodeReference(nodeId, scenarioGraph)) {
            const fragment = scenarioGraph.nodes.find((n) => nodeId.startsWith(n.id));
            return {
                fragmentId: fragment.ref?.id,
                fragmentNodeId: nodeId.replace(`${fragment.id}-`, ""),
                fragmentName: fragment.name,
            };
        }
        return null;
    };

    getNodesConnectedToOutput = (nodeId: NodeId, scenarioGraph: ScenarioGraph) => {
        const edges = this.edgesFromScenarioGraph(scenarioGraph);
        const outputEdges = this.getOutputEdges(nodeId, edges);
        const connected = outputEdges.filter((e) => e.to);
        return connected.map((e) => this.getNodeById(e.to, scenarioGraph));
    };

    getNodesConnectedToInput = (nodeId: NodeId, scenarioGraph: ScenarioGraph) => {
        const edges = this.edgesFromScenarioGraph(scenarioGraph);
        const connected = this.getInputEdges(nodeId, edges);
        return connected.map((e) => this.getNodeById(e.from, scenarioGraph));
    };
}

//TODO this pattern is not necessary, just export every public function as in actions.js
export default new NodeUtils();
