import { Typography } from "@mui/material";
import React from "react";

import { getUserSettings } from "../../../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../../../store/storeHelpers";
import type { NodeType } from "../../../../../types/node";
import MockExpressionField from "../../editors/expression/MockExpressionField";
import { NodeTable } from "../../NodeDetailsContent/NodeTable";
import { getFindAvailableVariables } from "../../NodeDetailsContent/selectors";
import { useGetNodeErrors, useIsEditMode, useSetProperty } from "../../useNodeTypeDetailsContentLogic";
import type { NodeState } from "../useNodeState";
import { InputData } from "./TestingContentElements/InputData";

interface Props {
    node: NodeType;
    onChange?: NodeState["onChange"];
}

export const TestingContent = ({ node, onChange }: Props) => {
    const settings = useAppSelector(getUserSettings);
    const showMockFieldOnEnrichers = settings["node.showMockFieldOnEnrichers"];

    const showMockField = showMockFieldOnEnrichers && node.type === "Enricher" && node.service.id !== "decision-table";
    const isEditMode = useIsEditMode({ onChange });
    const setProperty = useSetProperty({ onChange, node });
    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const [errors] = useGetNodeErrors(node);

    if (node.type === "Source") {
        return <InputData sourceId={node.id} />;
    }

    if (showMockField) {
        return (
            <NodeTable sx={settings["node.showInputsAndOutputs"] ? { margin: "0 16px" } : undefined}>
                <MockExpressionField
                    isEditMode={isEditMode}
                    editedNode={node}
                    showValidation
                    showSwitch
                    findAvailableVariables={findAvailableVariables}
                    setNodeDataAt={setProperty}
                    errors={errors}
                />
            </NodeTable>
        );
    }

    return <Typography p={2}>No testing settings available for selected node</Typography>;
};
