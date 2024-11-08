/* eslint-disable i18next/no-literal-string */
import { isEqual, uniqBy } from "lodash";
import ProcessUtils from "../../common/ProcessUtils";
import { Edge, EdgeKind, EdgeType, FragmentNodeType, NodeId, NodeType, ProcessDefinitionData, ScenarioGraph } from "../../types";
import { createEdge } from "../../reducers/graph/utils";
import { Scenario } from "../Process/types";

class NodeUtils {
    nodeIsFragment = (node: NodeType): node is FragmentNodeType => {
        return node.type === "FragmentInput";
    };

    nodeIsJoin = (node: NodeType): boolean => {
        return node && node.type === "Join";
    };

    nodesFromScenarioGraph = (scenarioGraph: ScenarioGraph): NodeType[] => scenarioGraph.nodes || [];

    edgesFromScenarioGraph = (scenarioGraph: ScenarioGraph) => scenarioGraph.edges || [];

    getProcessProperties = ({ name, scenarioGraph: { properties } }: Scenario, unsavedName?: string) => ({
        name: name || unsavedName,
        ...properties,
    });

    getNodeById = (nodeId: NodeId, scenarioGraph: ScenarioGraph) => this.nodesFromScenarioGraph(scenarioGraph).find((n) => n.id === nodeId);

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

    isAvailable = (node: NodeType, processDefinitionData): boolean => {
        return ProcessUtils.extractComponentDefinition(node, processDefinitionData.components) != null;
    };

    getOutputEdges = (nodeId: NodeId, edges: Edge[]): Edge[] => edges.filter((e) => e.from === nodeId);

    getEdgesForConnectedNodes = (nodeIds: NodeId[], scenarioGraph: ScenarioGraph): Edge[] =>
        scenarioGraph.edges?.filter((edge) => nodeIds.includes(edge.from) && nodeIds.includes(edge.to));

    getNextEdgeType = (allEdges: Edge[], node: NodeType, processDefinitionData: ProcessDefinitionData): EdgeType => {
        const edgesForNode = this.getEdgesAvailableForNode(node, processDefinitionData);

        if (edgesForNode.canChooseNodes) {
            return edgesForNode.edges[0];
        } else {
            const currentNodeEdges = allEdges.filter((edge) => edge.from === node.id);
            const currentEdgeTypes = currentNodeEdges.map((e) => e.edgeType);
            return edgesForNode.edges.find((et) => !currentEdgeTypes.find((currentType) => isEqual(currentType, et)));
        }
    };

    getEdgesAvailableForNode = (node: NodeType, processDefinitionData: ProcessDefinitionData, forInput?: boolean) => {
        const componentId = ProcessUtils.determineComponentId(node);
        //TODO: when we add more configuration for joins, probably more complex logic will be needed
        const edgesForNode = processDefinitionData.edgesForNodes
            .filter((e) => !forInput || e.isForInputDefinition === forInput)
            .find((e) => e.componentId === componentId);
        return edgesForNode || { edges: [null], canChooseNodes: false };
    };

    edgeLabel = (edge: Edge) => {
        const edgeType = edge?.edgeType;
        const type = edgeType?.type;
        switch (type) {
            case EdgeKind.fragmentOutput:
                return edgeType?.name;
            case EdgeKind.switchNext:
                return edgeType?.condition?.expression;
        }
        return this.edgeTypeLabel(type);
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

    canHaveMoreInputs = (node: NodeType, nodeInputs: Edge[], processDefinitionData: ProcessDefinitionData): boolean => {
        const edgesForNode = this.getEdgesAvailableForNode(node, processDefinitionData, true);
        const maxEdgesForNode = edgesForNode.edges.length;
        return this.hasInputs(node) && (edgesForNode.canChooseNodes || nodeInputs.length < maxEdgesForNode);
    };

    canHaveMoreOutputs = (node: NodeType, nodeOutputs: Edge[], processDefinitionData: ProcessDefinitionData): boolean => {
        const edgesForNode = this.getEdgesAvailableForNode(node, processDefinitionData, false);
        const maxEdgesForNode = edgesForNode.edges.length;
        return (
            this.hasOutputs(node, processDefinitionData) &&
            (edgesForNode.canChooseNodes || nodeOutputs.filter((e) => e.to).length < maxEdgesForNode)
        );
    };

    getFirstUnconnectedOutputEdge = (currentEdges: Edge[], availableEdges: EdgeType[], edgeType: EdgeType) => {
        const freeOutputEdges = currentEdges
            .filter((e) => !e.to)
            //we do this to skip e.g. edges that became incorrect/unavailable
            .filter((e) =>
                availableEdges.find((available) => available?.name == e?.edgeType?.name && available?.type == e?.edgeType?.type),
            );

        return freeOutputEdges.find((e) => e.edgeType === edgeType) || freeOutputEdges[0];
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
        return this.edgesFromScenarioGraph(scenarioGraph).filter((e) => e.from == nodeId);
    };

    edgeId = (edge: Edge): string => {
        return `${edge.from}-${edge.to}`;
    };

    noInputNodeTypes = ["Source", "FragmentInputDefinition", "StickyNote"];

    noOutputNodeTypes = ["Sink", "FragmentOutputDefinition", "StickyNote"];

    //TODO: methods below should be based on backend data, e.g. Fragment can have outputs or not - based on individual fragment...
    hasInputs = (node: NodeType): boolean => {
        return !this.noInputNodeTypes.includes(node?.type);
    };

    hasOutputs = (node: NodeType, processDefinitionData?: ProcessDefinitionData): boolean => {
        switch (node?.type) {
            case "FragmentInput": {
                const fragmentComponentId = ProcessUtils.determineComponentId(node);
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
}

//TODO this pattern is not necessary, just export every public function as in actions.js
export default new NodeUtils();
