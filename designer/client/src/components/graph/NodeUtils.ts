/* eslint-disable i18next/no-literal-string */
import { get, has, isEmpty, isEqual, uniqBy } from "lodash";
import * as ProcessDefinitionUtils from "../../common/ProcessDefinitionUtils";
import ProcessUtils from "../../common/ProcessUtils";
import {
    Edge,
    EdgeKind,
    EdgeType,
    FragmentNodeType,
    NodeId,
    NodeType,
    Process,
    ProcessDefinitionData,
    PropertiesType,
    UINodeType,
} from "../../types";
import { UnknownRecord } from "../../types/common";
import { createEdge } from "../../reducers/graph/utils";

class NodeUtils {
    isNode = (obj: UnknownRecord): obj is NodeType => {
        return !isEmpty(obj) && has(obj, "id") && has(obj, "type");
    };

    nodeType = (node: NodeType) => {
        return node.type ? node.type : "Properties";
    };

    nodeIsProperties = (node: UINodeType): node is PropertiesType => {
        const type = node && this.nodeType(node);
        return type === "Properties";
    };

    nodeIsFragment = (node): node is FragmentNodeType => {
        return this.nodeType(node) === "FragmentInput";
    };

    isPlainNode = (node: UINodeType) => {
        return !isEmpty(node) && !this.nodeIsProperties(node);
    };

    nodeIsJoin = (node: NodeType): boolean => {
        return node && this.nodeType(node) === "Join";
    };

    nodesFromProcess = (process: Process): NodeType[] => process.nodes || [];

    edgesFromProcess = (process: Process) => process.edges || [];

    getProcessProperties = ({ id, properties }: Process, name?: string) => ({ id: name || id, ...properties });

    getNodeById = (nodeId: NodeId, process: Process) => this.nodesFromProcess(process).find((n) => n.id === nodeId);

    getEdgeById = (edgeId: NodeId, process: Process) => this.edgesFromProcess(process).find((e) => this.edgeId(e) === edgeId);

    getAllNodesById = (nodeIds: NodeId[], process: Process) => {
        const allNodes = this.nodesFromProcess(process).filter((node) => nodeIds.includes(node.id));
        return uniqBy(allNodes, (n) => n.id);
    };

    getAllNodesByIdWithEdges = (ids: NodeId[], process: Process) => {
        const nodes = this.getAllNodesById(ids, process);
        const edgesForNodes = this.getEdgesForConnectedNodes(
            nodes.map((n) => n.id),
            process,
        );
        return {
            nodes: nodes,
            edges: edgesForNodes,
        };
    };

    isAvailable = (node: NodeType, processDefinitionData): boolean => {
        const availableIdsInComponentGroups = ProcessDefinitionUtils.getFlatComponents(processDefinitionData).map((component) =>
            ProcessUtils.findComponentId(component.node),
        );
        const nodeComponentId = ProcessUtils.findComponentId(node);
        return availableIdsInComponentGroups.includes(nodeComponentId);
    };

    getOutputEdges = (nodeId: NodeId, edges: Edge[]): Edge[] => edges.filter((e) => e.from === nodeId);

    getEdgesForConnectedNodes = (nodeIds: NodeId[], process: Process): Edge[] =>
        process.edges?.filter((edge) => nodeIds.includes(edge.from) && nodeIds.includes(edge.to));

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
        const componentId = ProcessUtils.findComponentId(node);
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
        process: Process,
        processDefinitionData: ProcessDefinitionData,
        previousEdge?: Edge,
    ): boolean => {
        const nodeInputs = this.nodeInputs(toId, process);
        //we do not want to include currently edited edge
        const nodeOutputs = this.nodeOutputs(fromId, process).filter((e) => e.from !== previousEdge?.from && e.to !== previousEdge?.to);

        const to = this.getNodeById(toId, process);
        const from = this.getNodeById(fromId, process);
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
        process,
        processDefinition,
    }: {
        fromNode: NodeType;
        toNode: NodeType;
        edgeType?: EdgeType;
        process: Process;
        processDefinition: ProcessDefinitionData;
    }): Edge => {
        const { edges: availableNodeEdges } = this.getEdgesAvailableForNode(fromNode, processDefinition);
        const currentNodeEdges = this.getOutputEdges(fromNode?.id, this.edgesFromProcess(process));
        const freeOutputEdge = this.getFirstUnconnectedOutputEdge(currentNodeEdges, availableNodeEdges, edgeType);
        return freeOutputEdge || createEdge(fromNode, toNode, edgeType, currentNodeEdges, processDefinition);
    };

    nodeInputs = (nodeId: NodeId, process: Process) => {
        return this.edgesFromProcess(process).filter((e) => e.to == nodeId);
    };

    nodeOutputs = (nodeId: NodeId, process: Process) => {
        return this.edgesFromProcess(process).filter((e) => e.from == nodeId);
    };

    edgeId = (edge: Edge): string => {
        return `${edge.from}-${edge.to}`;
    };

    noInputNodeTypes = ["Source", "FragmentInputDefinition"];

    noOutputNodeTypes = ["Sink", "FragmentOutputDefinition"];

    //TODO: methods below should be based on backend data, e.g. Fragment can have outputs or not - based on individual fragment...
    hasInputs = (node: NodeType): boolean => {
        return !this.noInputNodeTypes.includes(node?.type);
    };

    hasOutputs = (node: NodeType, processDefinitionData?: ProcessDefinitionData): boolean => {
        switch (node?.type) {
            case "FragmentInput": {
                const outputParameters =
                    processDefinitionData?.components["fragment-" + node.ref.id]?.outputParameters ||
                    Object.keys(node.ref.outputVariableNames);
                return outputParameters.length > 0;
            }
            default: {
                return !this.noOutputNodeTypes.includes(node?.type);
            }
        }
    };
}

//TODO this pattern is not necessary, just export every public function as in actions.js
export default new NodeUtils();
