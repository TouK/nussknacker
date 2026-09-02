import { cloneDeep, defaultsDeep, isArray, isEqual, isPlainObject, mergeWith, partition } from "lodash";

import ProcessUtils from "../../../common/ProcessUtils";
import { fakeComponentType } from "../../../reducers/selectors/appendAdditionalCreators";
import { fakeNodeCreatorType } from "../../../reducers/selectors/getCreator";
import type { AvailableEdgeType, ComponentGroup, Edge, NodeType, ProcessDefinitionData } from "../../../types";
import { EdgeKind } from "../../../types";
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
    // A `canChooseNodes` node publishes a template for new branches, not its outputs, so it has no main edge.
    const editedNodeEdges = editedNode && NodeUtils.getEdgesAvailableForNode(editedNode, processDefinitionData);
    const mainEntry = editedNodeEdges && !editedNodeEdges.canChooseNodes ? editedNodeEdges.edges[0] : undefined;
    const isMainEdge = (edge: Edge) => Boolean(mainEntry) && isEqual(edge.edgeType, mainEntry);
    // A named additional output (e.g. deduplication's "rejected") is specific to its custom component - it must not
    // masquerade as a Filter branch, a switch case or a fragment output, so on a switch to those node types it is
    // dropped rather than remapped; only the main continuation carries over.
    const isAdditionalCustomOutput = (edge: Edge) => edge.edgeType?.type === EdgeKind.customNodeOutput && !isMainEdge(edge);

    // Assigns `freeTypes` to `toRemap`, the edited node's main edge picking first, and drops what does not fit -
    // an entry may be undefined, which strips the type, but no edge may keep a kind or name that is not on offer.
    const remapMainFirst = (claimed: Set<Edge>, freeTypes: AvailableEdgeType[], toRemap: Edge[]): Edge[] => {
        const remaining = [...freeTypes];
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
    };

    // A named additional output cannot become a Filter branch, a switch case or a fragment output, so those
    // node types remap everything else and drop it.
    const remappableExceptAdditionalOutputs = (claimed: Set<Edge>) =>
        outputEdges.filter((edge) => !claimed.has(edge) && !isAdditionalCustomOutput(edge));

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
                remappableExceptAdditionalOutputs(claimed),
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
                remappableExceptAdditionalOutputs(claimed),
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
        // Unlike the node types above, a component declaring named outputs can take over another one's, so an
        // additional output is remapped here rather than dropped.
        return remapMainFirst(
            claimed,
            remaining,
            outputEdges.filter((edge) => !claimed.has(edge)),
        );
    }
    return [];
}

function compareNames(base: string, name: string): boolean {
    return new RegExp(`^${base}( [0-9]+)?( \\(copy [0-9]+\\))*$`).test(name);
}

function isCustomName(editedNode: NodeType, componentGroups: ComponentGroup[]) {
    const nodeCreatorType = fakeNodeCreatorType(editedNode);
    const componentId = nodeCreatorType ? `${fakeComponentType}-${nodeCreatorType}` : ProcessUtils.determineComponentId(editedNode);
    const component = componentGroups.flatMap((g) => g.components).find((c) => componentId === c.componentId);
    return !component || !compareNames(component.label, editedNode.$id || editedNode.id);
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
            id: isNameChanged || componentId ? editedNode.id : object.id,
            $id: isNameChanged ? editedNode.id : componentId ? object.id : null,
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
