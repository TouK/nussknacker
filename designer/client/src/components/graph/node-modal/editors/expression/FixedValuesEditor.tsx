import React from "react";
import Creatable from "react-select/creatable";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { Validator } from "../Validators";
import { ExpressionObj } from "./types";
import { isEmpty } from "lodash";
import { cx } from "@emotion/css";
import { styledSelect } from "../../../../../stylesheets/styledSelect";

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

export default class FixedValuesEditor extends React.Component<Props> {
    public static isSwitchableTo = (expressionObj: ExpressionObj, editorConfig) =>
        editorConfig.possibleValues.map((v) => v.expression).includes(expressionObj.expression) || isEmpty(expressionObj.expression);
    public static switchableToHint = () => "Switch to basic mode";
    public static notSwitchableToHint = () => "Expression must be one of the predefined values to switch to basic mode";

    currentOption(expressionObj: ExpressionObj, options: Option[]): Option {
        return (
            (expressionObj && options.find((option) => option.value === expressionObj.expression)) || // current value with label taken from options
            (expressionObj && { value: expressionObj.expression, label: expressionObj.expression }) || // current value is no longer valid option? Show it anyway, let user know. Validation should take care
            null
        ); // just leave undefined and let the user explicitly select one
    }

    render() {
        const { expressionObj, readOnly, onValueChange, className, showValidation, validators, editorConfig } = this.props;
        const options = getOptions(editorConfig.possibleValues);
        const currentOption = this.currentOption(expressionObj, options);

        // @ts-ignore
      const { control, input, valueContainer, singleValue, menuPortal, menu, menuList, menuOption } = styledSelect({});
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
    }
}
