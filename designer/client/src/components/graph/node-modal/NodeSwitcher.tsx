import React, { useCallback, useEffect, useMemo } from "react";

import ProcessUtils from "../../../common/ProcessUtils";
import { getConfiguredAdditionalComponents } from "../../../reducers/cloudData";
import { createUniqueName } from "../../../reducers/graph/utils";
import { getProcessDefinitionData } from "../../../reducers/selectors/getProcessDefinitionData";
import { getNodes } from "../../../reducers/selectors/graph";
import { isCloudInstance } from "../../../reducers/selectors/isCloudInstance";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import { editors } from "./editors/expression/Editor";
import type { ExpressionObj } from "./editors/expression/types";
import { EditorType, ExpressionLang } from "./editors/expression/types";
import { FormControl } from "./editors/FormControl";
import { FieldLabel } from "./FieldLabel";
import type { NodeGroupContentProps } from "./node/NodeGroupContent";
import { nodeValue } from "./NodeDetailsContent/NodeTableStyled";
import { replaceNodeData } from "./NodeSwitcherUtils";

type NodeSwitcherProps = NodeGroupContentProps & {
    componentsNamesToSelect: string[];
    creatorType: string;
    onCreate: () => void;
};

const creatorFakeId = "$NEW";

export function NodeSwitcher({ node: editedNode, onChange, edges, componentsNamesToSelect, creatorType, onCreate }: NodeSwitcherProps) {
    const processDefinitionData = useAppSelector(getProcessDefinitionData);

    const componentsToSelect = useMemo(() => {
        return processDefinitionData.componentGroups
            .flatMap((g) => g.components)
            .filter((c) => componentsNamesToSelect.includes(c.componentId));
    }, [componentsNamesToSelect, processDefinitionData.componentGroups]);

    const dispatch = useAppDispatch();
    const isCloud = useAppSelector(isCloudInstance);
    useEffect(() => {
        if (isCloud && processDefinitionData) {
            dispatch(getConfiguredAdditionalComponents());
        }
    }, [dispatch, isCloud, processDefinitionData]);

    const Editor = editors[EditorType.FIXED_VALUES_PARAMETER_EDITOR];

    const nodes = useAppSelector(getNodes);

    const onSelected = useCallback(
        (id: string) => {
            const component = componentsToSelect.find((c) => c.componentId === id);
            const { nextNode, nextEdges } = replaceNodeData(
                editedNode,
                {
                    ...component.node,
                    id: createUniqueName(
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

    const value = useMemo(
        () => ({
            language: ExpressionLang.String,
            expression: selectedId,
        }),
        [selectedId],
    );

    const onValueChange = useCallback(
        (value: ExpressionObj) => {
            if (typeof value?.expression !== "string") return;
            return value.expression === creatorFakeId ? onCreate() : onSelected(value.expression);
        },
        [onCreate, onSelected],
    );

    const editorConfig = useMemo(() => {
        const possibleValues = componentsToSelect.map((c) => ({ expression: c.componentId, label: c.label }));
        return {
            type: EditorType.FIXED_VALUES_PARAMETER_EDITOR,
            possibleValues: onCreate
                ? [
                      {
                          expression: creatorFakeId,
                          label: "create new...",
                      },
                      ...possibleValues,
                  ]
                : possibleValues,
        } as const;
    }, [componentsToSelect, onCreate]);

    if (!creatorType) return null;
    return (
        <FormControl sx={{ padding: "16px", marginX: "-16px", background: "rgba(0,0,0,.25)" }}>
            <FieldLabel label={"Component"} />
            <Editor
                editorConfig={editorConfig}
                className={nodeValue}
                fieldErrors={[]}
                onValueChange={onValueChange}
                expressionObj={value}
            />
        </FormControl>
    );
}
