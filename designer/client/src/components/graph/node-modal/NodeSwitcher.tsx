import { FormControl } from "@mui/material";
import { cloneDeep, defaultsDeep, isArray, isPlainObject, mergeWith } from "lodash";
import React, { useCallback, useEffect, useMemo } from "react";
import { useDispatch, useSelector } from "react-redux";
import ProcessUtils from "../../../common/ProcessUtils";
import { getConfiguredAdditionalComponents } from "../../../reducers/cloudData";
import { createUniqueNodeId } from "../../../reducers/graph/utils";
import { fakeComponentType, fakeNodeCreatorType } from "../../../reducers/selectors/getCreator";
import { getNodes } from "../../../reducers/selectors/graph";
import { getProcessDefinitionData } from "../../../reducers/selectors/settings";
import { Component, ComponentGroup, Edge, EdgeKind, NodeType, ProcessDefinitionData } from "../../../types";
import NodeUtils from "../NodeUtils";
import { editors, EditorType } from "./editors/expression/Editor";
import { ExpressionLang } from "./editors/expression/types";
import { FieldLabel } from "./FieldLabel";
import { NodeGroupContentProps } from "./node/NodeGroupContent";
import { nodeValue } from "./NodeDetailsContent/NodeTableStyled";

type NodeSwitcherProps = NodeGroupContentProps & {
    componentsNamesToSelect: string[];
    creatorType: string;
};

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
            case "nodeType":
                return val;
            case "fields":
            case "parameters":
            case "service.parameters":
                if (isArray(val)) {
                    return val.map((parameter) =>
                        mergeWithCustomizer(
                            parameter,
                            src.find((p) => p.name === parameter.name),
                            [...path, `[]`],
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

function adjustEdges(outputEdges: Edge[], nextNode: NodeType, processDefinitionData: ProcessDefinitionData) {
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
                if ([EdgeKind.switchNext, EdgeKind.switchDefault].includes(edge.edgeType?.type)) {
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
                if ([EdgeKind.fragmentOutput].includes(edge.edgeType?.type) && names.includes(edge.edgeType?.name)) {
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
        return outputEdges.map(({ edgeType, ...edge }) => edge);
    }
    return [];
}

function compareNames(base: string, name: string): boolean {
    return new RegExp(`^${base}( [0-9]+)?( \\(copy [0-9]+\\))*$`).test(name);
}

function isCustomName(editedNode: NodeType, componentGroups: ComponentGroup[]) {
    const nodeCreatorType = fakeNodeCreatorType(editedNode);
    const componentId = nodeCreatorType ? `${fakeComponentType}-${nodeCreatorType}` : ProcessUtils.determineComponentId(editedNode);
    const components = componentGroups.flatMap((g) => g.components);
    const component = components.find((c) => componentId === c.componentId);
    return !compareNames(component.label, editedNode.$id || editedNode.id);
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
    );
    return {
        nextNode,
        nextEdges,
    };
}

export function NodeSwitcher({ node: editedNode, onChange, edges, componentsNamesToSelect, creatorType }: NodeSwitcherProps) {
    const processDefinitionData = useSelector(getProcessDefinitionData);

    const componentsToSelect = useMemo(() => {
        return processDefinitionData.componentGroups
            .flatMap((g) => g.components)
            .filter((c) => componentsNamesToSelect.includes(c.componentId));
    }, [componentsNamesToSelect, processDefinitionData.componentGroups]);

    const dispatch = useDispatch();
    useEffect(() => {
        if (processDefinitionData) {
            dispatch(getConfiguredAdditionalComponents());
        }
    }, [dispatch, processDefinitionData]);

    const Editor = editors[EditorType.FIXED_VALUES_PARAMETER_EDITOR];

    const nodes = useSelector(getNodes);

    const onCreate = useCallback(() => {
        const tenantId = `55cf1666-e91e-42cb-80cd-f34f8b08e2b1`;
        window.open(`https://manage.staging-cloud.nussknacker.io/instance/${tenantId}/createEnricher/${creatorType}`);
    }, [creatorType]);

    const onSelected = useCallback(
        (id: string) => {
            const component = componentsToSelect.find((c) => c.componentId === id);
            const { nextNode, nextEdges } = replaceNodeData(
                editedNode,
                {
                    ...component.node,
                    id: createUniqueNodeId(
                        component.label,
                        nodes.map((n) => n.id).filter((i) => i !== editedNode.id),
                    ),
                },
                processDefinitionData,
                edges,
                creatorType,
                component.componentId,
            );
            return onChange(nextNode, nextEdges);
        },
        [componentsToSelect, creatorType, edges, editedNode, nodes, onChange, processDefinitionData],
    );

    const selectedId = useMemo(
        () => componentsToSelect.find((c) => c.componentId === ProcessUtils.determineComponentId(editedNode))?.componentId,
        [componentsToSelect, editedNode],
    );

    if (!creatorType) {
        return null;
    }

    return (
        <FormControl sx={{ padding: "16px", marginX: "-16px", background: "rgba(0,0,0,.25)" }}>
            <FieldLabel label={"Component"} />
            <Editor
                editorConfig={{
                    possibleValues: [
                        { expression: "$NEW", label: "create new..." },
                        ...componentsToSelect.map((c) => ({ expression: c.componentId, label: c.label })),
                    ],
                }}
                className={nodeValue}
                fieldErrors={[]}
                onValueChange={(id) => {
                    if (typeof id !== "string") throw "expression not expected here.";
                    return id === "$NEW" ? onCreate() : onSelected(id);
                }}
                expressionObj={{
                    language: ExpressionLang.String,
                    expression: selectedId,
                }}
            />
        </FormControl>
    );
}
