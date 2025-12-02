import { cx } from "@emotion/css";
import { FormControlLabel, Radio, RadioGroup, Stack, styled, useTheme } from "@mui/material";
import i18next from "i18next";
import { isEmpty } from "lodash";
import React from "react";
import Creatable from "react-select/creatable";

import { selectStyled } from "../../../../../stylesheets/SelectStyled";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { PreloadedIcon } from "../../../../toolbars/creator/ComponentIcon";
import type { FixedValuesOption } from "../../fragment-input-definition/item/types";
import { prepareEditor } from "./Editor";
import type { EditorConfigForType } from "./EditorConfig";
import { editorsParameters } from "./editorsParameters";
import type { ExpressionObj } from "./types";
import { EditorType } from "./types";

type FixedValuesEditorProps = {
    editorConfig:
        | EditorConfigForType<EditorType.FIXED_VALUES_PARAMETER_EDITOR>
        | EditorConfigForType<EditorType.FIXED_VALUES_WITH_ICON_PARAMETER_EDITOR>
        | EditorConfigForType<EditorType.FIXED_VALUES_WITH_RADIO_PARAMETER_EDITOR>;
    param?: $TodoType;
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

const NodeIcon = styled(PreloadedIcon)({
    minWidth: "1.5em",
    maxWidth: "1.5em",
    minHeight: "1.5em",
    maxHeight: "1.5em",
});

const StyledOptionLabel = styled("div")({
    width: "100%",
    lineHeight: "18px",
    whiteSpace: "pre-wrap",
    wordBreak: "break-all",
    overflowWrap: "break-word",
});

const truncateOptionLabel = (optionLabel: string) => {
    // TODO: Until we want have a better endpoint naming, we need to truncate it on the frontend side. Remove this logic when Backend ready
    return optionLabel?.replace(/-gateway\.(?:staging-cloud|cloud)\.nussknacker\.io\/topics/g, "(...)nussknacker.io"); // It will change URL https://light-pink-silkworm-gateway.staging-cloud.nussknacker.io/topics/http.example-input to https://light-pink-silkworm(...)nussknacker.io/http.example-input
};

export const FixedValuesEditor = prepareEditor<FixedValuesEditorProps>(
    (props) => {
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

        const {
            control,
            input,
            valueContainer,
            singleValue,
            menuPortal,
            menu,
            menuList,
            menuOption,
            indicatorSeparator,
            dropdownIndicator,
        } = selectStyled(theme);
        return editorConfig.type === EditorType.FIXED_VALUES_WITH_RADIO_PARAMETER_EDITOR ? (
            <div className={cx(className)}>
                <RadioGroup
                    value={currentOption.value}
                    onChange={(event) =>
                        onValueChange({
                            expression: event.target.value,
                            language: editorsParameters[EditorType.FIXED_VALUES_WITH_RADIO_PARAMETER_EDITOR].language,
                        })
                    }
                >
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
                    onChange={(newValue) =>
                        onValueChange({
                            expression: newValue.value,
                            language: editorsParameters[EditorType.FIXED_VALUES_PARAMETER_EDITOR].language,
                        })
                    }
                    options={options}
                    formatOptionLabel={(option) =>
                        option.icon ? (
                            <Stack direction={"row"} alignItems={"center"} spacing={1}>
                                <NodeIcon src={option.icon} />
                                <StyledOptionLabel role="option">{truncateOptionLabel(option.label)}</StyledOptionLabel>
                            </Stack>
                        ) : (
                            <StyledOptionLabel role="option">{truncateOptionLabel(option.label)}</StyledOptionLabel>
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
                        valueContainer: (base) => ({
                            ...valueContainer(base),
                        }),
                        singleValue: (base) => ({ ...singleValue(base, props.readOnly) }),
                    }}
                />

                {showValidation && <ValidationLabels fieldErrors={fieldErrors} />}
            </div>
        );
    },
    {
        isSwitchableTo: (expressionObj, editorConfig) =>
            editorConfig.possibleValues.map((v) => v.expression).includes(expressionObj.expression) || isEmpty(expressionObj.expression),
        notSwitchableToHint: () =>
            i18next.t(
                "editors.fixedValues.notSwitchableToHint",
                "Expression must be one of the predefined values to switch to {{editorName}} mode",
                { editorName: editorsParameters[EditorType.FIXED_VALUES_PARAMETER_EDITOR].displayName },
            ),
    },
);
