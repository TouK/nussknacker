import { NodeType, NodeValidationError, VariableTypes } from "../../../types";
import { useSelector } from "react-redux";
import { RootState } from "../../../reducers";
import { getNodeExpressionType } from "./NodeDetailsContent/selectors";
import MapVariable from "./MapVariable";
import React from "react";

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
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
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
