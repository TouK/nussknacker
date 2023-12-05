import React from "react";
import Creatable from "react-select/creatable";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { ExpressionObj } from "./types";
import { isEmpty } from "lodash";
import { ExtendedEditor } from "./Editor";
import { NodeValidationError } from "../../../../../types";
import { cx } from "@emotion/css";
import { selectStyled } from "../../../../../stylesheets/SelectStyled";
import { useTheme } from "@mui/material";

type Props = {
    editorConfig: $TodoType;
    expressionObj: ExpressionObj;
    onValueChange: (value: string) => void;
    readOnly: boolean;
    className: string;
    param?: $TodoType;
    showValidation: boolean;
    fieldErrors: NodeValidationError[];
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

export const FixedValuesEditor: ExtendedEditor<Props> = ({
    expressionObj,
    readOnly,
    onValueChange,
    className,
    showValidation,
    fieldErrors,
    editorConfig,
}: Props) => {
    const getCurrentOption = (expressionObj: ExpressionObj, options: Option[]): Option => {
const FixedValuesEditorComponent = (props: Props) => {
    const handleCurrentOption = (expressionObj: ExpressionObj, options: Option[]): Option => {
        return (
            (expressionObj && options.find((option) => option.value === expressionObj.expression)) || // current value with label taken from options
            (expressionObj && { value: expressionObj.expression, label: expressionObj.expression }) || // current value is no longer valid option? Show it anyway, let user know. Validation should take care
            null
        ); // just leave undefined and let the user explicitly select one
    };

    const { expressionObj, readOnly, onValueChange, className, showValidation, validators, editorConfig } = props;
    const options = getOptions(editorConfig.possibleValues);
    const currentOption = handleCurrentOption(expressionObj, options);
    const theme = useTheme();
    const options = getOptions(editorConfig.possibleValues);
    const currentOption = getCurrentOption(expressionObj, options);

    const { control, input, valueContainer, singleValue, menuPortal, menu, menuList, menuOption } = selectStyled(theme);
    return (
        <div className={cx(className)}>
            <Creatable
                value={currentOption}
                classNamePrefix={"test"}
                onChange={(newValue) => onValueChange(newValue.value)}
                options={options}
                isDisabled={readOnly}
                formatCreateLabel={(x) => x}
                menuPortalTarget={document.body}
                createOptionPosition={"first"}
                styles={{
                    input: (base) => ({ ...input(base) }),
                    control: (base, props) => ({
                        ...control(base, props.isFocused, props.isDisabled),
                    }),
                    menu: (base) => ({
                        ...menu(base),
                    }),
                    menuPortal: (base) => ({
                        ...menuPortal(base),
                    }),
                    menuList: (base) => ({
                        ...menuList(base),
                    }),
                    option: (base, props) => ({
                        ...menuOption(base, props.isSelected, props.isFocused),
                    }),
                    valueContainer: (base, props) => ({
                        ...valueContainer(base, props.hasValue),
                    }),
                    singleValue: (base, props) => ({ ...singleValue(base, props.isDisabled) }),
                }}
            />

            {showValidation && <ValidationLabels validators={validators} values={[currentOption.value]} />}
        </div>
    );
};

export default FixedValuesEditorComponent;

FixedValuesEditorComponent.isSwitchableTo = (expressionObj: ExpressionObj, editorConfig) =>
    editorConfig.possibleValues.map((v) => v.expression).includes(expressionObj.expression) || isEmpty(expressionObj.expression);
FixedValuesEditorComponent.switchableToHint = () => "Switch to basic mode";
FixedValuesEditorComponent.notSwitchableToHint = () => "Expression must be one of the predefined values to switch to basic mode";
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
            {showValidation && <ValidationLabels fieldErrors={fieldErrors} />}
        </div>
    );
};

FixedValuesEditor.isSwitchableTo = (expressionObj: ExpressionObj, editorConfig) =>
    editorConfig.possibleValues.map((v) => v.expression).includes(expressionObj.expression) || isEmpty(expressionObj.expression);
FixedValuesEditor.switchableToHint = () => "Switch to basic mode";
FixedValuesEditor.notSwitchableToHint = () => "Expression must be one of the predefined values to switch to basic mode";
