import { produce } from "immer";
import type { Dictionary } from "lodash";
import { cloneDeep, mapValues, reject, snakeCase, zipObject } from "lodash";

import type { NodesWithPositions } from "../../actions/nk/node";
import type { NodePosition } from "../../actions/nk/ui/layout";
import { snapToInt } from "../../actions/nk/ui/layout";
import ProcessUtils from "../../common/ProcessUtils";
import { ExpressionLang } from "../../components/graph/node-modal/editors/expression/types";
import { replaceValue } from "../../components/graph/node-modal/replaceValue";
import NodeUtils from "../../components/graph/NodeUtils";
import { deleteNode } from "../../components/graph/utils/graphUtils";
import type { Edge, EdgeType } from "../../types/edge";
import type { NodeId, NodeType } from "../../types/node";
import type { ProcessDefinitionData } from "../../types/scenarioGraph";
import type { GraphState } from "./types";

export const getUpdateStateAfterNodeIdChange = (currentId: NodeId, nextId: NodeId) =>
    produce((draft: GraphState) => {
        const { layout, testing, selectionState } = draft;
        if (layout) {
            layout.forEach((n) => {
                if (currentId === n.id) n.id = nextId;
            });
        }
        if (testing.testResults) {
            const { expressionEvaluationResults, nodeTransitionResults } = testing.testResults;
            expressionEvaluationResults[nextId] = expressionEvaluationResults[currentId];
            nodeTransitionResults?.forEach((r) => {
                if (r.sourceNodeId === currentId) r.sourceNodeId = nextId;
                if (r.destinationNodeId === currentId) r.destinationNodeId = nextId;
            });
        }
        const selected = selectionState.findIndex((c) => c === currentId);
        if (selected !== -1) {
            selectionState[selected] = nextId;
        }
    });

export function updateAfterNodeDelete({ layout, scenario, ...state }: GraphState, idToDelete: NodeId) {
    return {
        ...state,
        scenario: {
            ...scenario,
            scenarioGraph: deleteNode(scenario.scenarioGraph, idToDelete),
        },
        layout: layout.filter((n) => n.id !== idToDelete),
    };
}

function generateUniqueName(name: string, usedNames: string[], counter: number, isCopy: boolean): string {
    const newName = isCopy ? `${name} (copy ${counter})` : `${name} ${counter}`;
    return usedNames.includes(newName) ? generateUniqueName(name, usedNames, counter + 1, isCopy) : newName;
}

export function createUniqueName(name: string, usedNames: string[], isCopy = false): string {
    return name && !usedNames.includes(name) ? name : generateUniqueName(name, usedNames, 1, isCopy);
}

function getUniqueIds(initialIds: NodeId[], alreadyUsedIds: NodeId[], isCopy?: boolean): NodeId[] {
    return initialIds.reduce((uniqueIds, initialId) => {
        const reservedIds = alreadyUsedIds.concat(uniqueIds);
        const uniqueId = createUniqueName(initialId, reservedIds, isCopy);
        return uniqueIds.concat(uniqueId);
    }, []);
}

export function getIdMapping(currentNodes: Pick<NodeType, "id">[], newNodes: Pick<NodeType, "id">[], isCopy?: boolean) {
    const alreadyUsedIds = currentNodes.map((node) => node.id);
    const initialIds = newNodes.map(({ id }) => id);
    const uniqueIds = getUniqueIds(initialIds, alreadyUsedIds, isCopy);
    if (initialIds.length !== uniqueIds.length) {
        console.warn("Duplicated ids for node id mapping");
    }
    return zipObject(initialIds, uniqueIds);
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
    const idMapping = getIdMapping(
        currentNodes,
        newNodesWithPositions.map((p) => p.node),
        isCopy,
    );
    return {
        nodes: newNodesWithPositions.map(({ node, position }) =>
            produce(node, (draft) => {
                // adjust var names - only for new nodes
                if (!isCopy) {
                    replaceValue(draft, "ref.outputVariableNames", (value) =>
                        mapValues(value, (v, k) => snakeCase(`${idMapping[draft.id]} ${k}`)),
                    );
                    replaceValue(draft, "output", (value) => snakeCase(`${idMapping[draft.id]} ${value}`));
                    replaceValue(draft, "varName", (value) => snakeCase(`${idMapping[draft.id]} ${value}`));
                    replaceValue(draft, "outputName", (value) => snakeCase(`${idMapping[draft.id]} ${value}`));
                }
                replaceValue(draft, "id", (value) => idMapping[value]);
                replaceValue(draft, "branchParameters", (value) =>
                    value?.map((parameter) => ({
                        ...parameter,
                        branchId: idMapping[parameter.branchId],
                    })),
                );
                replaceValue(node, "additionalFields.layoutData", () => snapToInt(position), false);
            }),
        ),
        layout: newNodesWithPositions
            .filter(({ position }) => Boolean(position))
            .map(({ position, node }) => ({
                id: idMapping[node.id],
                position: snapToInt(position),
            })),
        idMapping,
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
        if (NodeUtils.nodeIsJoin(node)) {
            const newToNode = removeBranchParameter(node, from);
            return resultNodes.map((n) => {
                return n.id === to ? newToNode : n;
            });
        }
        return resultNodes;
    }, nodes);
}

export function enrichNodeWithProcessDependentData(
    originalNode: NodeType,
    processDefinitionData: ProcessDefinitionData,
    edges: Edge[],
): NodeType {
    const node = cloneDeep(originalNode);

    if (NodeUtils.nodeIsJoin(node)) {
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

    return node;
}
