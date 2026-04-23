import React, { useCallback } from "react";
import { v4 as uuid4 } from "uuid";

import type { TypedObjectTypingResult } from "../../../types/definition";
import type { Field, NodeType } from "../../../types/node";
import type { NodeValidationError, VariableTypes } from "../../../types/validation";
import { ExpressionLang } from "./editors/expression/types";
import Map from "./editors/map/Map";
import { NodeCommonDetailsDefinition } from "./NodeCommonDetailsDefinition";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

export interface MapVariableProps<F extends Field> {
    node: NodeType<F>;
    setProperty?: SetProperty;
    readOnly?: boolean;
    showValidation: boolean;

    errors: NodeValidationError[];
    removeElement: (namespace: string, uuid: string) => void;
    addElement: (property: string, element: F) => void;
    variableTypes: VariableTypes;
    expressionType?: Partial<TypedObjectTypingResult>;
    isValidating: boolean;
}

function MapVariable<F extends Field>(props: MapVariableProps<F>): React.JSX.Element {
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
