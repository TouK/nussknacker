import { isEqual } from "lodash";
import React from "react";

import type { RootState } from "../../../reducers";
import { useAppSelector } from "../../../store/storeHelpers";
import type { NodeType } from "../../../types/node";
import type { NodeValidationError, VariableTypes } from "../../../types/validation";
import MapVariable from "./MapVariable";
import { getNodeExpressionType } from "./NodeDetailsContent/selectors";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

export function VariableBuilder({
    addElement,
    errors,
    isEditMode,
    node,
    removeElement,
    setProperty,
    showValidation,
    variableTypes,
}: {
    addElement: (...args: unknown[]) => unknown;
    errors: NodeValidationError[];
    isEditMode?: boolean;
    node: NodeType;
    removeElement: (property: keyof NodeType, uuid: string) => void;
    setProperty: SetProperty;
    showValidation?: boolean;
    variableTypes?: VariableTypes;
}): React.JSX.Element {
    const nodeExpressionType = useAppSelector((state: RootState) => getNodeExpressionType(state)(node.id), isEqual);
    return (
        <MapVariable
            removeElement={removeElement}
            setProperty={setProperty}
            node={node}
            addElement={addElement}
            readOnly={!isEditMode}
            showValidation={showValidation}
            variableTypes={variableTypes}
            errors={errors || []}
            expressionType={nodeExpressionType}
        />
    );
}
