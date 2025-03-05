import React from "react";
import Creatable from "react-select/creatable";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { ExpressionObj } from "./types";
import { isEmpty } from "lodash";
import { cx } from "@emotion/css";
import { selectStyled } from "../../../../../stylesheets/SelectStyled";
import { FormControlLabel, Radio, RadioGroup, Stack, styled, useTheme } from "@mui/material";
import { EditorType, ExtendedEditor } from "./Editor";
import { FieldError } from "../Validators";
import { FixedValuesOption } from "../../fragment-input-definition/item";
import { PreloadedIcon } from "../../../../toolbars/creator/ComponentIcon";

type Props = {
    editorConfig: $TodoType;
    expressionObj: ExpressionObj;
    onValueChange: (value: string) => void;
    readOnly: boolean;
    className: string;
    param?: $TodoType;
    showValidation: boolean;
    fieldErrors: FieldError[];
};

interface Option {
    label: string;
    value: string;
    icon: string | null;
}

function getOptions(values: FixedValuesOption[]): Option[] {
    return values.map((value) => ({
        value: value.expression,
        label: value.label,
        icon: value.icon,
    }));
}

export const FixedValuesEditor: ExtendedEditor<Props> = (props: Props) => {
    const handleCurrentOption = (expressionObj: ExpressionObj, options: Option[]): Option => {
        return (
            (expressionObj && options.find((option) => option.value === expressionObj.expression)) || // current value with label taken from options
            (expressionObj && { value: expressionObj.expression, label: expressionObj.expression, icon: null }) || // current value is no longer valid option? Show it anyway, let user know. Validation should take care
            null
        ); // just leave undefined and let the user explicitly select one
    };

    const { expressionObj, readOnly, onValueChange, className, showValidation, editorConfig, fieldErrors } = props;
    const options = getOptions(editorConfig.possibleValues);
    const currentOption = handleCurrentOption(expressionObj, options);
    const theme = useTheme();
    const NodeIcon = styled(PreloadedIcon)({
        minWidth: "1.5em",
        maxWidth: "1.5em",
        minHeight: "1.5em",
        maxHeight: "1.5em",
    });

    const { control, input, valueContainer, singleValue, menuPortal, menu, menuList, menuOption, indicatorSeparator, dropdownIndicator } =
        selectStyled(theme);
    return editorConfig.type === EditorType.FIXED_VALUES_WITH_RADIO_PARAMETER_EDITOR ? (
        <div className={cx(className)}>
            <RadioGroup value={currentOption.value} onChange={(event) => onValueChange(event.target.value)}>
                {options.map((option: Option) => {
                    const label = option.value === props.param?.defaultValue ? `${option.label} (default)` : option.label;
                    return <FormControlLabel key={option.value} value={option.value} control={<Radio />} label={label} />;
                })}
            </RadioGroup>
        </div>
    ) : (
        <div className={cx(className)}>
            <Creatable
                value={currentOption}
                classNamePrefix={"test"}
                onChange={(newValue) => onValueChange(newValue.value)}
                options={options}
                formatOptionLabel={(option) =>
                    option.icon ? (
                        <Stack direction={"row"} alignItems={"center"} spacing={1}>
                            <NodeIcon src={option.icon} />
                            <div>{option.label}</div>
                        </Stack>
                    ) : (
                        option.label
                    )
                }
                isDisabled={readOnly}
                formatCreateLabel={(x) => x}
                menuPortalTarget={document.body}
                createOptionPosition={"first"}
                styles={{
                    input: (base) => ({ ...input(base) }),
                    control: (base, props) => ({
                        ...control(base, props.isFocused, props.isDisabled, !isEmpty(fieldErrors)),
                    }),
                    dropdownIndicator: (base) => ({
                        ...dropdownIndicator(base),
                    }),
                    indicatorSeparator: (base) => ({
                        ...indicatorSeparator(base),
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
                        ...menuOption(base, props.isSelected, props.isDisabled),
                    }),
                    valueContainer: (base, props) => ({
                        ...valueContainer(base),
                    }),
                    singleValue: (base) => ({ ...singleValue(base, props.readOnly) }),
                }}
            />

            {showValidation && <ValidationLabels fieldErrors={fieldErrors} />}
        </div>
    );
};

FixedValuesEditor.isSwitchableTo = (expressionObj: ExpressionObj, editorConfig) =>
    editorConfig.possibleValues.map((v) => v.expression).includes(expressionObj.expression) || isEmpty(expressionObj.expression);
FixedValuesEditor.switchableToHint = () => "Switch to basic mode";
FixedValuesEditor.notSwitchableToHint = () => "Expression must be one of the predefined values to switch to basic mode";
