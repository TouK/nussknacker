import { isEqual } from "lodash";
import React from "react";
import { Expression, NodeValidationError, VariableTypes } from "../../../../../types";
import { NodeValue } from "../../node";
import EditableEditor from "../EditableEditor";
import { FieldError, Validator } from "../Validators";

interface MapValueProps {
    value: Expression;
    fieldError: FieldError;
    variableTypes: VariableTypes;
    onChange?: (value: unknown) => void;
    showValidation?: boolean;
    readOnly?: boolean;
    isMarked?: boolean;
    validationLabelInfo?: string;
}

export default React.memo(function MapValue(props: MapValueProps): JSX.Element {
    const { value, isMarked, showValidation, readOnly, onChange, fieldError, variableTypes, validationLabelInfo } = props;

    return (
        <NodeValue className="field">
            <EditableEditor
                fieldError={fieldError}
                isMarked={isMarked}
                readOnly={readOnly}
                showValidation={showValidation}
                onValueChange={onChange}
                expressionObj={value}
                rowClassName={" "}
                valueClassName={" "}
                variableTypes={variableTypes}
                validationLabelInfo={validationLabelInfo}
            />
        </NodeValue>
    );
}, isEqual);
