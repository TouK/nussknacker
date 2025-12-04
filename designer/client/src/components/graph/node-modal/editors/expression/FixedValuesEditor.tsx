import { cx } from "@emotion/css";
import { FormControlLabel, Radio, RadioGroup, styled } from "@mui/material";
import i18next from "i18next";
import { isEmpty } from "lodash";
import React, { useCallback, useMemo } from "react";

import { PreloadedIcon } from "../../../../toolbars/creator/ComponentIcon";
import type { FixedValuesOption } from "../../fragment-input-definition/item/types";
import type { OnValueChange } from "./Editor";
import { prepareEditor } from "./Editor";
import type { EditorConfigForType } from "./EditorConfig";
import { editorsParameters } from "./editorsParameters";
import { SelectVariant } from "./FixedValuesEditor_SelectVariant";
import type { ExpressionObj } from "./types";
import { EditorType } from "./types";

export type FixedValuesEditorProps = {
    editorConfig:
        | EditorConfigForType<EditorType.FIXED_VALUES_PARAMETER_EDITOR>
        | EditorConfigForType<EditorType.FIXED_VALUES_WITH_ICON_PARAMETER_EDITOR>
        | EditorConfigForType<EditorType.FIXED_VALUES_WITH_RADIO_PARAMETER_EDITOR>;
    param?: $TodoType; // TODO: really used?
};

export interface Option {
    label: string;
    value: string;
    icon: string | null;
}

export function getOptions(values: FixedValuesOption[]): Option[] {
    return values.map((value) => ({
        value: value.expression,
        label: value.label,
        icon: value.icon,
    }));
}

export const NodeIcon = styled(PreloadedIcon)({
    minWidth: "1.5em",
    maxWidth: "1.5em",
    minHeight: "1.5em",
    maxHeight: "1.5em",
    alignSelf: "center",
});

export const StyledOptionLabel = styled("div")({
    width: "100%",
    lineHeight: "18px",
    whiteSpace: "pre-wrap",
    wordBreak: "break-all",
    overflowWrap: "break-word",
});

export const truncateOptionLabel = (optionLabel: string) => {
    // TODO: Until we want have a better endpoint naming, we need to truncate it on the frontend side. Remove this logic when Backend ready
    return optionLabel?.replace(/-gateway\.(?:staging-cloud|cloud)\.nussknacker\.io\/topics/g, "(...)nussknacker.io"); // It will change URL https://light-pink-silkworm-gateway.staging-cloud.nussknacker.io/topics/http.example-input to https://light-pink-silkworm(...)nussknacker.io/http.example-input
};

function RadioVariant({
    className,
    currentOption,
    onValueChange,
    options,
    param,
}: {
    className?: string;
    currentOption: Option;
    onValueChange: OnValueChange;
    options: Option[];
    param?: FixedValuesEditorProps["param"];
}) {
    return (
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
                    const label = option.value === param?.defaultValue ? `${option.label} (default)` : option.label;
                    return <FormControlLabel key={option.value} value={option.value} control={<Radio />} label={label} />;
                })}
            </RadioGroup>
        </div>
    );
}

export const FixedValuesEditor = prepareEditor<FixedValuesEditorProps>(
    ({ className, editorConfig, expressionObj, fieldErrors, onValueChange, param, readOnly, showValidation }) => {
        const handleCurrentOption = useCallback((expressionObj: ExpressionObj, options: Option[]): Option => {
            return (
                (expressionObj && options.find((option) => option.value === expressionObj.expression)) || // current value with label taken from options
                (expressionObj && { value: expressionObj.expression, label: expressionObj.expression, icon: null }) || // current value is no longer valid option? Show it anyway, let user know. Validation should take care
                null
            ); // just leave undefined and let the user explicitly select one
        }, []);

        const options = useMemo<Option[]>(() => getOptions(editorConfig.possibleValues), [editorConfig.possibleValues]);
        const currentOption = useMemo<Option>(
            () => handleCurrentOption(expressionObj, options),
            [expressionObj, handleCurrentOption, options],
        );

        if (editorConfig.type === EditorType.FIXED_VALUES_WITH_RADIO_PARAMETER_EDITOR) {
            return (
                <RadioVariant
                    className={className}
                    currentOption={currentOption}
                    onValueChange={onValueChange}
                    options={options}
                    param={param}
                />
            );
        }

        return (
            <SelectVariant
                className={className}
                currentOption={currentOption}
                fieldErrors={fieldErrors}
                onValueChange={onValueChange}
                options={options}
                readOnly={readOnly}
                showValidation={showValidation}
            />
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
