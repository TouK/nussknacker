import { styled } from "@mui/material";
import i18next from "i18next";
import { isEmpty } from "lodash";
import React from "react";

import { PreloadedIcon } from "../../../../toolbars/creator/ComponentIcon";
import type { FixedValuesOption } from "../../fragment-input-definition/item/types";
import { prepareEditor } from "./Editor";
import type { EditorConfigForType } from "./EditorConfig";
import { editorsParameters } from "./editorsParameters";
import { ReactSelectCreatableVariant as SelectVariant } from "./FixedValuesEditor_ReactSelectCreatableVariant";
import type { ExpressionObj } from "./types";
import { EditorType } from "./types";

export type FixedValuesEditorProps = {
    editorConfig:
        | EditorConfigForType<EditorType.FIXED_VALUES_PARAMETER_EDITOR>
        | EditorConfigForType<EditorType.FIXED_VALUES_WITH_ICON_PARAMETER_EDITOR>;
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

export const handleCurrentOption = (expressionObj: ExpressionObj, options: Option[]): Option => {
    // just leave undefined and let the user explicitly select one
    if (!expressionObj) return null;

    // current value with label taken from options
    const found = options.find((option) => option.value === expressionObj.expression);
    if (found) return found;

    // current value is no longer valid option? Show it anyway, let user know. Validation should take care
    return {
        value: expressionObj.expression,
        label: expressionObj.expression,
        icon: null,
    };
};

export const FixedValuesEditor = prepareEditor<FixedValuesEditorProps>(
    ({ className, editorConfig, expressionObj, fieldErrors, onValueChange, readOnly, showValidation }) => {
        return (
            <SelectVariant
                className={className}
                currentOption={handleCurrentOption(expressionObj, getOptions(editorConfig.possibleValues))}
                fieldErrors={fieldErrors}
                onValueChange={onValueChange}
                options={getOptions(editorConfig.possibleValues)}
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
