import { cloneDeep, defaultsDeep, isArray, isEqual, isPlainObject, mergeWith, partition } from "lodash";

import { determineComponentId } from "../../../common/componentUtils";
import { fakeComponentType } from "../../../reducers/selectors/appendAdditionalCreators";
import { fakeNodeCreatorType } from "../../../reducers/selectors/getCreator";
import type { ComponentGroup } from "../../../types/component";
import type { AvailableEdgeType, Edge, EdgeType } from "../../../types/edge";
import { EdgeKind } from "../../../types/edge";
import type { NodeType } from "../../../types/node";
import type { ProcessDefinitionData } from "../../../types/scenarioGraph";
import NodeUtils from "../NodeUtils";
import { ExpressionLang } from "./editors/expression/types";

function mergeWithCustomizer<T>(object: T, source: T, path: string[] = []) {
    return mergeWith(object, source, (val, src, key) => {
        const fullPath = [...path, key];
        switch (fullPath.join(".")) {
            case "additionalFields.layoutData":
                return src;
            case "additionalFields.creatorType":
                return val;
            case "type":
            case "service.id":
            case "ref.typ":
            case "nodeType":
                return val || null;
            case "fields":
            case "parameters":
            case "service.parameters":
                if (isArray(val)) {
                    return val.map((parameter) =>
                        mergeWithCustomizer(
                            parameter,
                            src.find((p) => p.name === parameter.name),
                            [...fullPath, `[]`],
                        ),
                    );
                }
                return undefined;
        }

        if (isPlainObject(val) || isPlainObject(src)) {
            return mergeWithCustomizer(val, src, fullPath);
        }

        return src;
    });
}

export function adjustEdges(outputEdges: Edge[], nextNode: NodeType, processDefinitionData: ProcessDefinitionData, editedNode?: NodeType) {
    // A `canChooseNodes` node has no main output: its entries are a template for new branches, and Choice's
    // `NextSwitch("true")` template would match every branch the user left on the default condition.
    const editedNodeEdges = editedNode && NodeUtils.getEdgesAvailableForNode(editedNode, processDefinitionData);
    const mainEntry = editedNodeEdges && !editedNodeEdges.canChooseNodes ? editedNodeEdges.edges[0] : undefined;
    const isMainEdge = (edge: Edge) => Boolean(mainEntry) && isEqual(edge.edgeType, mainEntry);
    // A named additional output (e.g. deduplication's "rejected") is specific to its custom component - it must not
    // masquerade as a Filter branch, a switch case or a fragment output, so on a switch to those node types it is
    // dropped rather than remapped; only the main continuation carries over.
    const isAdditionalCustomOutput = (edge: Edge) => edge.edgeType?.type === EdgeKind.customNodeOutput && !isMainEdge(edge);

    // Assigns `freeTypes` to the unclaimed edges, the edited node's main edge picking first, and drops what does
    // not fit - no edge may end up with an undefined kind or name.
    const remapMainFirst = (claimed: Set<Edge>, freeTypes: EdgeType[]): Edge[] => {
        const remaining = [...freeTypes];
        const remapped = new Map<Edge, EdgeType>();
        const [mainEdges, restEdges] = partition(
            outputEdges.filter((edge) => !claimed.has(edge) && !isAdditionalCustomOutput(edge)),
            isMainEdge,
        );
        for (const edge of [...mainEdges, ...restEdges]) {
            if (remaining.length === 0) {
                break;
            }
            remapped.set(edge, remaining.shift());
        }
        return outputEdges.flatMap((edge) => {
            if (claimed.has(edge)) {
                return [edge];
            }
            const entry = remapped.get(edge);
            return entry ? [{ ...edge, edgeType: entry }] : [];
        });
    };

    switch (nextNode.type) {
        case "Filter": {
            const kinds = [EdgeKind.filterTrue, EdgeKind.filterFalse];
            const claimed = new Set<Edge>();
            for (const edge of outputEdges) {
                const index = kinds.findIndex((kind) => edge.edgeType?.type === kind);
                if (index >= 0) {
                    kinds.splice(index, 1);
                    claimed.add(edge);
                }
            }
            return remapMainFirst(
                claimed,
                kinds.map((kind) => ({ type: kind })),
            );
        }
        case "Switch": {
            return outputEdges.flatMap((edge) => {
                if (edge.edgeType?.type === EdgeKind.switchNext || edge.edgeType?.type === EdgeKind.switchDefault) {
                    return [edge];
                }
                if (isAdditionalCustomOutput(edge)) {
                    return [];
                }
                return [
                    {
                        ...edge,
                        edgeType: {
                            type: EdgeKind.switchNext,
                            condition: {
                                language: ExpressionLang.SpEL,
                                expression: "true",
                            },
                        },
                    },
                ];
            });
        }
        case "FragmentInput": {
            const names = Object.keys(nextNode.ref?.outputVariableNames);
            const claimed = new Set<Edge>();
            for (const edge of outputEdges) {
                const index = edge.edgeType?.type === EdgeKind.fragmentOutput ? names.indexOf(edge.edgeType.name) : -1;
                if (index >= 0) {
                    names.splice(index, 1);
                    claimed.add(edge);
                }
            }
            return remapMainFirst(
                claimed,
                names.map((name) => ({ type: EdgeKind.fragmentOutput, name })),
            );
        }
    }
    if (NodeUtils.hasOutputs(nextNode, processDefinitionData)) {
        const { edges: availableEdges, canChooseNodes } = NodeUtils.getEdgesAvailableForNode(nextNode, processDefinitionData);
        // A split-like node has room for any number of unnamed continuations; foreign types are stripped.
        if (canChooseNodes) {
            return outputEdges.map((edge) => {
                const { edgeType: _edgeType, ...rest } = edge;
                return rest;
            });
        }
        // First every edge whose type the new component declares keeps its slot, wherever it sits in the list; only
        // then the rest are remapped onto the leftover entries in declaration order (an unnamed entry strips the
        // type), the edited node's main edge picking first - so collapsing to a single-output component keeps the
        // main subtree - and what does not fit is dropped; a multi-output component accepts no unnamed edges, so
        // stripping the surplus would break the scenario.
        const remaining = [...availableEdges];
        const claimed = new Set<Edge>();
        for (const edge of outputEdges) {
            const index = remaining.findIndex((available) => isEqual(available, edge.edgeType));
            if (index >= 0) {
                remaining.splice(index, 1);
                claimed.add(edge);
            }
        }
        const toRemap = outputEdges.filter((edge) => !claimed.has(edge));
        const remapped = new Map<Edge, AvailableEdgeType>();
        const [mainEdges, restEdges] = partition(toRemap, isMainEdge);
        for (const edge of [...mainEdges, ...restEdges]) {
            if (remaining.length === 0) {
                break;
            }
            remapped.set(edge, remaining.shift());
        }
        return outputEdges.flatMap((edge) => {
            if (claimed.has(edge)) {
                return [edge];
            }
            if (!remapped.has(edge)) {
                return [];
            }
            const entry = remapped.get(edge);
            const { edgeType: _edgeType, ...rest } = edge;
            return [entry ? { ...rest, edgeType: entry } : rest];
        });
    }
    return [];
}

function compareNames(base: string, name: string): boolean {
    return new RegExp(`^${base}( [0-9]+)?( \\(copy [0-9]+\\))*$`).test(name);
}

function isCustomName(editedNode: NodeType, componentGroups: ComponentGroup[]) {
    const nodeCreatorType = fakeNodeCreatorType(editedNode);
    const componentId = nodeCreatorType ? `${fakeComponentType}-${nodeCreatorType}` : determineComponentId(editedNode);
    const component = componentGroups.flatMap((g) => g.components).find((c) => componentId === c.componentId);
    return !component || !compareNames(component.label, editedNode.$name ?? editedNode.name);
}

export function replaceNodeData(
    editedNode: NodeType,
    nextNodeData: NodeType,
    processDefinitionData: ProcessDefinitionData,
    outputEdges: Edge[] = [],
    creatorType: string = null,
    componentId?: string,
) {
    const object = cloneDeep(nextNodeData);

    const componentGroups = processDefinitionData.componentGroups;

    const isNameChanged = isCustomName(editedNode, componentGroups);
    const nextNode = defaultsDeep(
        {
            id: editedNode.id,
            $name: isNameChanged ? editedNode.$name ?? editedNode.name : componentId ? object.name : null,
        },
        mergeWithCustomizer(
            {
                ...object,
                additionalFields: {
                    ...object.additionalFields,
                    creatorType: object.additionalFields?.creatorType || creatorType,
                },
            },
            editedNode,
        ),
    );

    const nextEdges = adjustEdges(
        outputEdges.filter((e) => e.to),
        nextNode,
        processDefinitionData,
        editedNode,
    );
    return {
        nextNode,
        nextEdges,
    };
}
