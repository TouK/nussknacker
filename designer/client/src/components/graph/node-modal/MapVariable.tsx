import React, { useCallback } from "react";
import { Field, NodeType, NodeValidationError, TypedObjectTypingResult, VariableTypes } from "../../../types";
import { ExpressionLang } from "./editors/expression/types";
import Map from "./editors/map/Map";
import { NodeCommonDetailsDefinition } from "./NodeCommonDetailsDefinition";
import { v4 as uuid4 } from "uuid";

export interface MapVariableProps<F extends Field> {
    node: NodeType<F>;
    setProperty?: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    readOnly?: boolean;
    showValidation: boolean;
    renderFieldLabel: (label: string) => React.ReactNode;
    errors: NodeValidationError[];
    removeElement: (namespace: string, uuid: string) => void;
    addElement: (property: string, element: F) => void;
    variableTypes: VariableTypes;
    expressionType?: Partial<TypedObjectTypingResult>;
}

function MapVariable<F extends Field>(props: MapVariableProps<F>): JSX.Element {
    const { removeElement, addElement, variableTypes, expressionType, errors, ...passProps } = props;
    const { node, ...mapProps } = passProps;

    const addField = useCallback(
        (namespace, field) => {
            const newField: Field = { uuid: uuid4(), name: "", expression: { expression: "", language: ExpressionLang.SpEL } };
            addElement(namespace, field || newField);
        },
        [addElement],
    );

    return (
        <NodeCommonDetailsDefinition {...props} errors={errors} outputName="Variable Name" outputField="varName">
            <Map
                {...mapProps}
                errors={errors}
                label="Fields"
                namespace="fields"
                fields={node.fields}
                removeField={removeElement}
                addField={addField}
                variableTypes={variableTypes}
                expressionType={expressionType}
            />
        </NodeCommonDetailsDefinition>
    );
}

export default MapVariable;
