import React from "react";
import Creatable from "react-select/creatable";
import styles from "../../../../../stylesheets/select.styl";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { Validator } from "../Validators";
import { ExpressionObj } from "./types";
import { isEmpty } from "lodash";

type Props = {
    editorConfig: $TodoType;
    expressionObj: ExpressionObj;
    onValueChange: (value: string) => void;
    readOnly: boolean;
    className: string;
    param?: $TodoType;
    showValidation: boolean;
    validators: Array<Validator>;
};

interface Option {
    label: string;
    value: string;
}

function getOptions(
    values: {
        expression: string;
        label: string;
    }[],
): Option[] {
    return values.map((value) => ({
        value: value.expression,
        label: value.label,
    }));
}

export function FixedValuesEditor({ expressionObj, readOnly, onValueChange, className, showValidation, validators, editorConfig }: Props) {
    const getCurrentOption = (expressionObj: ExpressionObj, options: Option[]): Option => {
        return (
            (expressionObj && options.find((option) => option.value === expressionObj.expression)) || // current value with label taken from options
            (expressionObj && { value: expressionObj.expression, label: expressionObj.expression }) || // current value is no longer valid option? Show it anyway, let user know. Validation should take care
            null
        ); // just leave undefined and let the user explicitly select one
    };

    const options = getOptions(editorConfig.possibleValues);
    const currentOption = getCurrentOption(expressionObj, options);

    return (
        <div className={`node-value-select ${className}`}>
            <Creatable
                classNamePrefix={styles.nodeValueSelect}
                value={currentOption}
                onChange={(newValue) => onValueChange(newValue.value)}
                options={options}
                isDisabled={readOnly}
                formatCreateLabel={(x) => x}
                menuPortalTarget={document.body}
                createOptionPosition={"first"}
            />
            {showValidation && <ValidationLabels validators={validators} values={[currentOption.value]} />}
        </div>
    );
}

FixedValuesEditor.isSwitchableTo = (expressionObj: ExpressionObj, editorConfig) =>
    editorConfig.possibleValues.map((v) => v.expression).includes(expressionObj.expression) || isEmpty(expressionObj.expression);
FixedValuesEditor.switchableToHint = () => "Switch to basic mode";
FixedValuesEditor.notSwitchableToHint = () => "Expression must be one of the predefined values to switch to basic mode";

export default FixedValuesEditor;
