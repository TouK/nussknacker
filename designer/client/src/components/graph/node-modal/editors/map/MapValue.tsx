import { isEqual } from "lodash";
import React from "react";
import { Expression, VariableTypes } from "../../../../../types";
import { NodeValue } from "../../node";
import { EditableEditor } from "../EditableEditor";
import { FieldError } from "../Validators";

interface MapValueProps {
    value: Expression;
    fieldErrors: FieldError[];
    variableTypes: VariableTypes;
    onChange?: (value: unknown) => void;
    showValidation?: boolean;
    readOnly?: boolean;
    isMarked?: boolean;
    validationLabelInfo?: string;
}

export default React.memo(function MapValue(props: MapValueProps): JSX.Element {
    const { value, isMarked, showValidation, readOnly, onChange, fieldErrors, variableTypes, validationLabelInfo } = props;

    return (
        <NodeValue className="field">
            <EditableEditor
                fieldErrors={fieldErrors}
                isMarked={isMarked}
                readOnly={readOnly}
                showValidation={showValidation}
                onValueChange={onChange}
                expressionObj={value}
                valueClassName={" "}
                variableTypes={variableTypes}
                validationLabelInfo={validationLabelInfo}
            />
        </NodeValue>
    );
}, isEqual);
