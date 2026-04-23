import { isEqual } from "lodash";
import React from "react";

import type { Expression } from "../../../../../types/node";
import type { VariableTypes } from "../../../../../types/validation";
import { NodeValue } from "../../node/NodeValue";
import { EditableEditor } from "../EditableEditor";
import type { ExpressionObj } from "../expression/types";
import type { FieldError } from "../Validators";

interface MapValueProps {
    value: Expression;
    fieldErrors: FieldError[];
    variableTypes: VariableTypes;
    onChange?: (value: ExpressionObj) => void;
    showValidation?: boolean;
    readOnly?: boolean;
    isMarked?: boolean;
    validationLabelInfo?: string;
    isValidating: boolean;
}

export default React.memo(function MapValue(props: MapValueProps): React.JSX.Element {
    const { value, isMarked, showValidation, readOnly, onChange, fieldErrors, variableTypes, validationLabelInfo, isValidating } = props;

    return (
        <NodeValue className="field">
            <EditableEditor
                fieldErrors={fieldErrors}
                isMarked={isMarked}
                readOnly={readOnly}
                showValidation={showValidation}
                onValueChange={onChange}
                expressionObj={value}
                variableTypes={variableTypes}
                validationLabelInfo={validationLabelInfo}
                showSwitch={false}
                isValidating={isValidating}
            />
        </NodeValue>
    );
}, isEqual);
