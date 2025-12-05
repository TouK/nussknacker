import React from "react";

import { getUserSettings } from "../../../../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../../../../store/storeHelpers";
import MockExpressionField from "../../../editors/expression/MockExpressionField";
import { NodeTable } from "../../../NodeDetailsContent/NodeTable";
import { getFindAvailableVariables } from "../../../NodeDetailsContent/selectors";
import { useGetNodeErrors, useIsEditMode, useSetProperty, useValidation } from "../../../useNodeTypeDetailsContentLogic";
import type { TestingContentProps } from "../TestingContent";

export const MockResponse = ({ node, edges, onChange }: TestingContentProps) => {
    const settings = useAppSelector(getUserSettings);

    const isEditMode = useIsEditMode({ onChange });
    const setProperty = useSetProperty({ onChange, node });
    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const [errors] = useGetNodeErrors(node);

    useValidation({ node, showValidation: true, edges });

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
