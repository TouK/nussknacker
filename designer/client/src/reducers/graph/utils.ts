import { cloneDeep, map, reject, zipWith } from "lodash";
import { Layout, NodePosition, NodesWithPositions } from "../../actions/nk";
import ProcessUtils from "../../common/ProcessUtils";
import { ExpressionLang } from "../../components/graph/node-modal/editors/expression/types";
import NodeUtils from "../../components/graph/NodeUtils";
import { Edge, EdgeType, NodeId, NodeType, ProcessDefinitionData } from "../../types";
import { GraphState } from "./types";

export function updateLayoutAfterNodeIdChange(layout: Layout, oldId: NodeId, newId: NodeId): Layout {
    return map(layout, (n) => (oldId === n.id ? { ...n, id: newId } : n));
}

export function updateAfterNodeDelete(state: GraphState, idToDelete: NodeId) {
    return {
        ...state,
        scenario: {
            ...state.scenario,
            scenarioGraph: {
                ...state.scenario.scenarioGraph,
                nodes: state.scenario.scenarioGraph.nodes.filter((n) => n.id !== idToDelete),
            },
        },
        layout: state.layout.filter((n) => n.id !== idToDelete),
    };
}

function generateUniqueNodeId(initialId: NodeId, usedIds: NodeId[], nodeCounter: number, isCopy: boolean): NodeId {
    const newId = isCopy ? `${initialId} (copy ${nodeCounter})` : `${initialId} ${nodeCounter}`;
    return usedIds.includes(newId) ? generateUniqueNodeId(initialId, usedIds, nodeCounter + 1, isCopy) : newId;
}

function createUniqueNodeId(initialId: NodeId, usedIds: NodeId[], isCopy: boolean): NodeId {
    return initialId && !usedIds.includes(initialId) ? initialId : generateUniqueNodeId(initialId, usedIds, 1, isCopy);
}

function getUniqueIds(initialIds: string[], alreadyUsedIds: string[], isCopy: boolean) {
    return initialIds.reduce((uniqueIds, initialId) => {
        const reservedIds = alreadyUsedIds.concat(uniqueIds);
        const uniqueId = createUniqueNodeId(initialId, reservedIds, isCopy);
        return uniqueIds.concat(uniqueId);
    }, []);
}

export function prepareNewNodesWithLayout(
    state: GraphState,
    nodesWithPositions: NodesWithPositions,
    isCopy: boolean,
): { layout: NodePosition[]; nodes: NodeType[]; uniqueIds?: NodeId[] } {
    const {
        layout,
        scenario: {
            scenarioGraph: { nodes = [] },
        },
    } = state;

    const alreadyUsedIds = nodes.map((node) => node.id);
    const initialIds = nodesWithPositions.map((nodeWithPosition) => nodeWithPosition.node.id);
    const uniqueIds = getUniqueIds(initialIds, alreadyUsedIds, isCopy);

    const updatedNodes = zipWith(nodesWithPositions, uniqueIds, ({ node }, id) => {
        const nodeCopy = cloneDeep(node);

        if (nodeCopy.branchParameters) {
            nodeCopy.branchParameters = nodeCopy.branchParameters.map((branchParameter) => {
                branchParameter.branchId = uniqueIds.find((uniqueId) => uniqueId.includes(branchParameter.branchId));
                return branchParameter;
            });
        }

        nodeCopy.id = id;
        return nodeCopy;
    });
    const updatedLayout = zipWith(nodesWithPositions, uniqueIds, ({ position }, id) => ({ id, position }));

    return {
        nodes: [...nodes, ...updatedNodes],
        layout: [...layout, ...updatedLayout],
        uniqueIds,
    };
}

export function addNodesWithLayout(state: GraphState, { nodes, layout }: ReturnType<typeof prepareNewNodesWithLayout>): GraphState {
    return {
        ...state,
        scenario: {
            ...state.scenario,
            scenarioGraph: {
                ...state.scenario.scenarioGraph,
                nodes,
            },
        },
        layout,
    };
}

export function createEdge(
    fromNode: NodeType,
    toNode: NodeType,
    edgeType: EdgeType,
    nodeOutputEdges: Edge[],
    processDefinitionData: ProcessDefinitionData,
) {
    const baseEdge = { from: fromNode?.id, to: toNode?.id };
    const adjustedEdgeType = edgeType || NodeUtils.getNextEdgeType(nodeOutputEdges, fromNode, processDefinitionData);
    return adjustedEdgeType ? { ...baseEdge, edgeType: adjustedEdgeType } : baseEdge;
}

export function removeBranchParameter(node: NodeType, branchId: NodeId) {
    const { branchParameters, ...clone } = cloneDeep(node);
    return {
        ...clone,
        branchParameters: reject(branchParameters, (parameter) => parameter.branchId === branchId),
    };
}

export function adjustBranchParametersAfterDisconnect(nodes: NodeType[], removedEdges: Pick<Edge, "from" | "to">[]): NodeType[] {
    return removedEdges.reduce((resultNodes, { from, to }) => {
        const node = resultNodes.find((n) => n.id === to);
        if (node && NodeUtils.nodeIsJoin(node)) {
            const newToNode = removeBranchParameter(node, from);
            return resultNodes.map((n) => {
                return n.id === to ? newToNode : n;
            });
        } else {
            return resultNodes;
        }
    }, nodes);
}

export function enrichNodeWithProcessDependentData(
    originalNode: NodeType,
    processDefinitionData: ProcessDefinitionData,
    edges: Edge[],
): NodeType {
    const node = cloneDeep(originalNode);

    switch (NodeUtils.nodeType(node)) {
        case "Join": {
            const parameters = ProcessUtils.extractComponentDefinition(node, processDefinitionData.components)?.parameters;
            const declaredBranchParameters = parameters?.filter((p) => p.branchParam) || [];
            const incomingEdges = edges.filter((e) => e.to === node.id);
            const branchParameters = incomingEdges.map((edge) => {
                const branchId = edge.from;
                const existingBranchParams = node.branchParameters.find((p) => p.branchId === branchId);
                const parameters = declaredBranchParameters.map((branchParamDef) => {
                    const existingParamValue = existingBranchParams?.parameters?.find((p) => p.name === branchParamDef.name);
                    if (!existingParamValue) {
                        const templateParamValue = node.branchParametersTemplate?.find((p) => p.name === branchParamDef.name);
                        if (!templateParamValue) {
                            // We need to have this fallback to some template for situation when it is existing node and it has't got
                            // defined parameters filled. see note in DefinitionPreparer on backend side TODO: remove it after API refactor
                            return {
                                name: branchParamDef.name,
                                expression: {
                                    expression: `#${branchParamDef.name}`,
                                    language: ExpressionLang.SpEL,
                                },
                            };
                        }
                        return cloneDeep(templateParamValue);
                    }
                    return existingParamValue;
                });

                return { branchId, parameters };
            });

            return { ...node, branchParameters };
        }
    }

    return node;
}
