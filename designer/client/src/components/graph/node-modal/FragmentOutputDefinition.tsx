import React, { useCallback } from "react";
import { v4 as uuid4 } from "uuid";

import type { RootState } from "../../../reducers";
import { useAppSelector } from "../../../store/storeHelpers";
import type { Field } from "../../../types/node";
import { ExpressionLang } from "./editors/expression/types";
import Map from "./editors/map/Map";
import type { MapVariableProps } from "./MapVariable";
import { NodeCommonDetailsDefinition } from "./NodeCommonDetailsDefinition";
import { getNodeExpressionType } from "./NodeDetailsContent/selectors";

interface Props<F extends Field> extends Omit<MapVariableProps<F>, "expressionType" | "readOnly"> {
    isEditMode?: boolean;
}

function FragmentOutputDefinition<F extends Field>(props: Props<F>): React.JSX.Element {
    const { removeElement, addElement, variableTypes, isEditMode, ...passProps } = props;
    const { node, ...mapProps } = passProps;
    const readOnly = !isEditMode;

    const expressionType = useAppSelector((state: RootState) => getNodeExpressionType(state)(node.id));

    const addField = useCallback(
        (namespace: string, field) => {
            const newField: Field = { uuid: uuid4(), name: "", expression: { expression: "", language: ExpressionLang.SpEL } };
            addElement(namespace, field || newField);
        },
        [addElement],
    );

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
