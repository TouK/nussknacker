import React from "react";

import { getUserSettings } from "../../../../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../../../../store/storeHelpers";
import type { NodeType } from "../../../../../../types/node";
import MockExpressionField from "../../../editors/expression/MockExpressionField";
import { NodeTable } from "../../../NodeDetailsContent/NodeTable";
import { getFindAvailableVariables } from "../../../NodeDetailsContent/selectors";
import { useGetNodeErrors, useIsEditMode, useSetProperty } from "../../../useNodeTypeDetailsContentLogic";
import type { NodeState } from "../../useNodeState";

interface Props {
    node: NodeType;
    onChange?: NodeState["onChange"];
}

export const MockResponse = ({ node, onChange }: Props) => {
    const settings = useAppSelector(getUserSettings);

    const isEditMode = useIsEditMode({ onChange });
    const setProperty = useSetProperty({ onChange, node });
    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const [errors] = useGetNodeErrors(node);

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
};
