import { cloneDeep, Dictionary, map, reject, zipObject, zipWith } from "lodash";
import { Layout, NodePosition, NodesWithPositions } from "../../actions/nk";
import ProcessUtils from "../../common/ProcessUtils";
import { ExpressionLang } from "../../components/graph/node-modal/editors/expression/types";
import NodeUtils from "../../components/graph/NodeUtils";
import { BranchParams, Edge, EdgeType, NodeId, NodeType, ProcessDefinitionData } from "../../types";
import { GraphState } from "./types";
import { StickyNote } from "../../common/StickyNote";
import { createStickyNoteId } from "../../types/stickyNote";

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

function adjustBranchParameters(branchParameters: BranchParams[], uniqueIds: string[]) {
    return branchParameters?.map(({ branchId, ...branchParameter }: BranchParams) => ({
        ...branchParameter,
        branchId: uniqueIds.find((uniqueId) => uniqueId.includes(branchId)),
    }));
}

export function prepareNewNodesWithLayout(
    currentNodes: NodeType[] = [],
    newNodesWithPositions: NodesWithPositions,
    isCopy: boolean,
): {
    layout: NodePosition[];
    nodes: NodeType[];
    idMapping: Dictionary<string>;
} {
    const newNodes = newNodesWithPositions.map(({ node }) => node);
    const newPositions = newNodesWithPositions.map(({ position }) => position);
    const alreadyUsedIds = currentNodes.map((node) => node.id);
    const initialIds = newNodes.map(({ id }) => id);
    const uniqueIds = getUniqueIds(initialIds, alreadyUsedIds, isCopy);

    return {
        nodes: zipWith(newNodes, uniqueIds, (node, id) => ({
            ...node,
            id,
            branchParameters: adjustBranchParameters(node.branchParameters, uniqueIds),
        })),
        layout: zipWith(newPositions, uniqueIds, (position, id) => ({
            id,
            position,
        })),
        idMapping: zipObject(initialIds, uniqueIds),
    };
}

export function removeStickyNoteFromLayout(state: GraphState, stickyNoteId: number): { layout: NodePosition[]; stickyNotes: StickyNote[] } {
    const { layout } = state;
    const updatedStickyNotes = state.stickyNotes.filter((n) => n.noteId != stickyNoteId);
    const updatedLayout = updatedStickyNotes.map((stickyNote) => {
        return { id: createStickyNoteId(stickyNote.noteId), position: stickyNote.layoutData };
    });
    return {
        stickyNotes: [...updatedStickyNotes],
        layout: [...layout, ...updatedLayout],
    };
}

export function prepareNewStickyNotesWithLayout(
    state: GraphState,
    stickyNotes: StickyNote[],
): { layout: NodePosition[]; stickyNotes: StickyNote[] } {
    const { layout } = state;
    const updatedLayout = stickyNotes.map((stickyNote) => {
        return { id: createStickyNoteId(stickyNote.noteId), position: stickyNote.layoutData };
    });
    return {
        stickyNotes: [...stickyNotes],
        layout: [...layout, ...updatedLayout],
    };
}

export function addNodesWithLayout(
    state: GraphState,
    changes: {
        nodes: NodeType[];
        layout: NodePosition[];
        edges?: Edge[];
    },
): GraphState {
    const { nodes = [], edges = [], ...scenarioGraph } = state.scenario.scenarioGraph;
    const nextNodes = [...nodes, ...changes.nodes];
    const nextEdges = changes.edges || edges;
    const nextLayout = [...state.layout, ...changes.layout];
    return {
        ...state,
        scenario: {
            ...state.scenario,
            scenarioGraph: {
                ...scenarioGraph,
                nodes: nextNodes,
                edges: nextEdges,
            },
        },
        layout: nextLayout,
    };
}


export function addStickyNotesWithLayout(
    state: GraphState,
    { stickyNotes, layout }: ReturnType<typeof prepareNewStickyNotesWithLayout>,
): GraphState {
    return {
        ...state,
        stickyNotes: stickyNotes,
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

    switch (node.type) {
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
