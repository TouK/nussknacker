import { isEqual } from "lodash";
import React from "react";
import { useSelector } from "react-redux";

import type { RootState } from "../../../reducers";
import type { NodeType, NodeValidationError, VariableTypes } from "../../../types";
import MapVariable from "./MapVariable";
import { getNodeExpressionType } from "./NodeDetailsContent/selectors";
import type { SetProperty } from "./NodeTypeDetailsContent";

export function VariableBuilder({
    addElement,
    errors,
    isEditMode,
    node,
    removeElement,
    renderFieldLabel,
    setProperty,
    showValidation,
    variableTypes,
}: {
    addElement: (...args: any[]) => any;
    errors: NodeValidationError[];
    isEditMode?: boolean;
    node: NodeType;
    removeElement: (property: keyof NodeType, uuid: string) => void;
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: SetProperty;
    showValidation?: boolean;
    variableTypes?: VariableTypes;
}): JSX.Element {
    const nodeExpressionType = useSelector((state: RootState) => getNodeExpressionType(state)(node.id));

    return (
        <MapVariable
            renderFieldLabel={renderFieldLabel}
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
