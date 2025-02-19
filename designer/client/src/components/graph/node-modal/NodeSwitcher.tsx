import { FormControl } from "@mui/material";
import { cloneDeep, defaultsDeep, isArray, isObject, mergeWith } from "lodash";
import React, { useCallback, useEffect, useMemo } from "react";
import { useDispatch, useSelector } from "react-redux";
import { getConfiguredAdditionalComponents } from "../../../reducers/cloudData";
import { getCreatorType } from "../../../reducers/selectors/getCreator";
import { getProcessDefinitionData } from "../../../reducers/selectors/settings";
import { Component, Edge, EdgeKind, NodeType, ProcessDefinitionData } from "../../../types";
import NodeUtils from "../NodeUtils";
import { editors, EditorType } from "./editors/expression/Editor";
import { ExpressionLang } from "./editors/expression/types";
import { FieldLabel } from "./FieldLabel";
import { NodeGroupContentProps } from "./node/NodeGroupContent";
import { nodeValue } from "./NodeDetailsContent/NodeTableStyled";

type NodeSwitcherProps = NodeGroupContentProps & {
    componentsNamesToSelect: string[];
};

function mergeWithCustomizer<T>(object: T, source: T, path: string[] = []) {
    return mergeWith(object, source, (val, src, key) => {
        const fullPath = [...path, key].join(".");

        switch (fullPath) {
            case "id":
                return src;
            case "service.id":
                return val;
            case "parameters":
            case "service.parameters":
                if (!isArray(val)) break;
                return val.map((parameter) =>
                    mergeWithCustomizer(
                        parameter,
                        src.find((p) => p.name === parameter.name),
                        [...path, `[]`],
                    ),
                );
        }

        if (isObject(val) && isObject(src)) {
            return mergeWithCustomizer(val, src, [...path, key]);
        }

        return val;
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

export function replaceNodeData(
    editedNode: NodeType,
    nextNodeData: NodeType,
    processDefinitionData: ProcessDefinitionData,
    outputEdges: Edge[] = [],
    creatorType?: string,
    componentId?: string,
) {
    const { type, ...source } = editedNode;

    const object = cloneDeep(nextNodeData);
    const nextNode = defaultsDeep(
        {
            additionalFields: {
                virtualNode: creatorType,
                componentId,
            },
        },
        mergeWithCustomizer(object, source, []),
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

export function NodeSwitcher({ node: editedNode, onChange, edges, componentsNamesToSelect = [] }: NodeSwitcherProps) {
    const processDefinitionData = useSelector(getProcessDefinitionData);

    const componentsToSelect = useMemo(() => {
        return processDefinitionData.componentGroups
            .flatMap((g) => g.components)
            .filter((c) => componentsNamesToSelect.includes(c.componentId));
    }, [componentsNamesToSelect, processDefinitionData.componentGroups]);

    const creatorType = useMemo(() => getCreatorType(editedNode), [editedNode]);

    const dispatch = useDispatch();
    useEffect(() => {
        if (processDefinitionData) {
            dispatch(getConfiguredAdditionalComponents());
        }
    }, [dispatch, processDefinitionData]);

    const switchNode = useCallback(
        (node: NodeType, componentId?: string) => {
            const { nextNode, nextEdges } = replaceNodeData(editedNode, node, processDefinitionData, edges, creatorType, componentId);
            return onChange(nextNode, nextEdges);
        },
        [creatorType, edges, editedNode, onChange, processDefinitionData],
    );

    const Editor = editors[EditorType.FIXED_VALUES_PARAMETER_EDITOR];

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
                    if (id === "$NEW") {
                        const tenantId = `55cf1666-e91e-42cb-80cd-f34f8b08e2b1`;
                        window.open(`https://manage.staging-cloud.nussknacker.io/instance/${tenantId}/createEnricher/${creatorType}`);
                    }
                    const component = componentsToSelect.find((c) => c.componentId === id);
                    switchNode(component.node, component.componentId);
                }}
                expressionObj={{
                    language: ExpressionLang.String,
                    expression: componentsToSelect.find((c) => c.componentId === editedNode.additionalFields.componentId)?.componentId,
                }}
            />
        </FormControl>
    );
}
