import { cloneDeep, defaultsDeep, isArray, isPlainObject, mergeWith } from "lodash";

import { determineComponentId } from "../../../common/componentUtils";
import { fakeComponentType } from "../../../reducers/selectors/appendAdditionalCreators";
import { fakeNodeCreatorType } from "../../../reducers/selectors/getCreator";
import type { ComponentGroup } from "../../../types/component";
import type { Edge } from "../../../types/edge";
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

export function adjustEdges(outputEdges: Edge[], nextNode: NodeType, processDefinitionData: ProcessDefinitionData) {
    switch (nextNode.type) {
        case "Filter": {
            let edgeKinds = [EdgeKind.filterTrue, EdgeKind.filterFalse];
            return outputEdges.map((edge) => {
                for (const kind of edgeKinds) {
                    if (edge.edgeType?.type === kind) {
                        edgeKinds = edgeKinds.filter((k) => edge.edgeType?.type !== k);
                        return edge;
                    }
                }
                return {
                    ...edge,
                    edgeType: {
                        type: edgeKinds.shift(),
                    },
                };
            });
        }
        case "Switch": {
            return outputEdges.map((edge) => {
                if ([EdgeKind.switchNext, EdgeKind.switchDefault].includes(EdgeKind[edge.edgeType?.type])) {
                    return edge;
                }
                return {
                    ...edge,
                    edgeType: {
                        type: EdgeKind.switchNext,
                        condition: {
                            language: ExpressionLang.SpEL,
                            expression: "true",
                        },
                    },
                };
            });
        }
        case "FragmentInput": {
            let names = Object.keys(nextNode.ref?.outputVariableNames);
            outputEdges
                .filter(({ edgeType }) => {
                    return edgeType?.type === EdgeKind.fragmentOutput && names.includes(edgeType?.name);
                })
                .forEach(({ edgeType }) => {
                    names = names.filter((n) => n !== edgeType.name);
                });

            return outputEdges.map((edge) => {
                if ([EdgeKind.fragmentOutput].includes(EdgeKind[edge.edgeType?.type]) && names.includes(edge.edgeType?.name)) {
                    return edge;
                }
                return {
                    ...edge,
                    edgeType: {
                        type: EdgeKind.fragmentOutput,
                        name: names.shift(),
                    },
                };
            });
        }
    }
    if (NodeUtils.hasOutputs(nextNode, processDefinitionData)) {
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        return outputEdges.map(({ edgeType, ...edge }) => edge);
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
    return !component || !compareNames(component.label, editedNode.$name ?? editedNode.name ?? editedNode.id);
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
            $name: isNameChanged ? editedNode.$name ?? editedNode.name : componentId ? object.name ?? object.id : null,
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
    );
    return {
        nextNode,
        nextEdges,
    };
}
