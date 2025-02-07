import { FormControl } from "@mui/material";
import { cloneDeep, defaultsDeep, isArray, mergeWith } from "lodash";
import React, { useCallback, useEffect, useMemo } from "react";
import { useDispatch, useSelector } from "react-redux";
import { getConfiguredAdditionalComponents } from "../../../reducers/cloudData";
import { getCreatorType } from "../../../reducers/selectors/getCreator";
import { getProcessDefinitionData } from "../../../reducers/selectors/settings";
import { Component } from "../../../types";
import NodeUtils from "../NodeUtils";
import { editors, EditorType } from "./editors/expression/Editor";
import { ExpressionLang } from "./editors/expression/types";
import { FieldLabel } from "./FieldLabel";
import { NodeGroupContentProps } from "./node/NodeGroupContent";
import { nodeValue } from "./NodeDetailsContent/NodeTableStyled";

type NodeSwitcherProps = NodeGroupContentProps & {
    componentsNamesToSelect: string[];
};

export function NodeSwitcher({ node, onChange, edges, componentsNamesToSelect = [] }: NodeSwitcherProps) {
    const processDefinitionData = useSelector(getProcessDefinitionData);

    const componentsToSelect = useMemo(() => {
        return processDefinitionData.componentGroups
            .flatMap((g) => g.components)
            .filter((c) => componentsNamesToSelect.includes(c.componentId));
    }, [componentsNamesToSelect, processDefinitionData.componentGroups]);

    const creatorType = useMemo(() => getCreatorType(node), [node]);

    const dispatch = useDispatch();
    useEffect(() => {
        if (processDefinitionData) {
            dispatch(getConfiguredAdditionalComponents());
        }
    }, [dispatch, processDefinitionData]);

    const switchNode = useCallback(
        (selectedComponent: Component) => {
            const { type, ...source } = node;

            const customizer = (arg1, arg2, key: string) => {
                if (key === "parameters" && isArray(arg1)) {
                    return arg1.map((parameter) => arg2.find(({ name }) => name === parameter.name) || parameter);
                }
            };

            const nextNode = defaultsDeep(
                { additionalFields: { virtualNode: creatorType, componentId: selectedComponent.componentId } },
                mergeWith(cloneDeep(selectedComponent.node), source, customizer),
            );

            return onChange(nextNode, NodeUtils.hasOutputs(nextNode, processDefinitionData) ? edges : []);
        },
        [creatorType, edges, node, onChange, processDefinitionData],
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
                    switchNode(component);
                }}
                expressionObj={{
                    language: ExpressionLang.String,
                    expression: componentsToSelect.find((c) => c.componentId === node.additionalFields.componentId)?.componentId,
                }}
            />
        </FormControl>
    );
}
