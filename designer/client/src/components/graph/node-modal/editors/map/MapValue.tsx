import { isEqual } from "lodash";
import React from "react";
import { Expression, NodeValidationError, VariableTypes } from "../../../../../types";
import { NodeValue } from "../../node";
import EditableEditor from "../EditableEditor";
import { Validator } from "../Validators";

interface MapValueProps {
    value: Expression;
    fieldErrors?: NodeValidationError[];
    variableTypes: VariableTypes;
    onChange?: (value: unknown) => void;
    validators?: Validator[];
    showValidation?: boolean;
    readOnly?: boolean;
    isMarked?: boolean;
    validationLabelInfo?: string;
    fieldName: string;
}

export default React.memo(function MapValue(props: MapValueProps): JSX.Element {
    const { value, isMarked, validators, showValidation, readOnly, onChange, fieldErrors, variableTypes, validationLabelInfo, fieldName } = props;

    console.log("field errors", fieldErrors);
    return (
        <NodeValue className="field">
            <EditableEditor
                fieldErrors={fieldErrors}
                isMarked={isMarked}
                readOnly={readOnly}
                showValidation={showValidation}
                onValueChange={onChange}
                expressionObj={value}
                rowClassName={" "}
                valueClassName={" "}
                variableTypes={variableTypes}
                validationLabelInfo={validationLabelInfo}
                fieldName={fieldName}
            />
        </NodeValue>
    );
}, isEqual);
