import React, { useCallback } from "react";
import { Field } from "../../../types";
import { ExpressionLang } from "./editors/expression/types";
import Map from "./editors/map/Map";
import { MapVariableProps } from "./MapVariable";
import { NodeCommonDetailsDefinition } from "./NodeCommonDetailsDefinition";
import { useSelector } from "react-redux";
import { RootState } from "../../../reducers";
import { getNodeExpressionType } from "./NodeDetailsContent/selectors";
import { v4 as uuid4 } from "uuid";

interface Props<F extends Field> extends Omit<MapVariableProps<F>, "expressionType" | "readOnly"> {
    isEditMode?: boolean;
}

function FragmentOutputDefinition<F extends Field>(props: Props<F>): JSX.Element {
    const { removeElement, addElement, variableTypes, isEditMode, ...passProps } = props;
    const { node, ...mapProps } = passProps;
    const readOnly = !isEditMode;

    const expressionType = useSelector((state: RootState) => getNodeExpressionType(state)(node.id));

    const addField = useCallback(
        (namespace: string, field) => {
            const newField: Field = { uuid: uuid4(), name: "", expression: { expression: "", language: ExpressionLang.SpEL } };
            addElement(namespace, field || newField);
        },
        [addElement],
    );

    console.log("FragmentOutputDefinition", props.fieldErrors);
    return (
        <NodeCommonDetailsDefinition {...passProps} readOnly={readOnly} outputName="Output name" outputField="outputName">
            <Map
                {...mapProps}
                readOnly={readOnly}
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

export default FragmentOutputDefinition;
