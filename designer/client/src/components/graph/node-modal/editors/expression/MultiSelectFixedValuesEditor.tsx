import type { SelectChangeEvent } from "@mui/material";
import { Box, Chip, MenuItem, OutlinedInput, Select } from "@mui/material";
import i18next from "i18next";
import { isEqual, uniq } from "lodash";
import React, { useCallback, useEffect } from "react";

import ValidationLabels from "../../../../modals/ValidationLabels";
import { ValuesList } from "../../aggregate/groupBy/valuesList";
import { useStream } from "../../node/useStream";
import { prepareEditor } from "./Editor";
import type { EditorConfigForType } from "./EditorConfig";
import { editorsParameters } from "./editorsParameters";
import { EditorType, ExpressionLang } from "./types";

function getExpressionFromValues(values: string[]) {
    return {
        language: editorsParameters[EditorType.MULTI_SELECT_EDITOR].language,
        expression: JSON.stringify(values),
    };
}

export const MultiSelectFixedValuesEditor = prepareEditor<{ editorConfig: EditorConfigForType<EditorType.MULTI_SELECT_EDITOR> }>(
    ({ editorConfig, expressionObj, onValueChange, showValidation, fieldErrors }) => {
        const [value$, emit, values] = useStream<string[]>(() => {
            try {
                return JSON.parse(expressionObj.expression);
            } catch {
                return [];
            }
        }, true);

        useEffect(() => {
            const subscription = value$.skipDuplicates(isEqual).observe((values) => {
                onValueChange(getExpressionFromValues(uniq(values)));
            });
            return subscription.unsubscribe;
        }, [onValueChange, value$]);

        const handleChange = useCallback(
            ({ target }: SelectChangeEvent<string[]>) => {
                emit(Array.isArray(target.value) ? target.value : []);
            },
            [emit],
        );

        const removeByIndex = useCallback(
            (i: number) => {
                emit((current) => current.filter((value) => value !== current[i]));
            },
            [emit],
        );

        const renderValue = useCallback(
            (selected: string[]) => (
                <ValuesList
                    values={selected}
                    ChipComponent={Chip}
                    onRemove={removeByIndex}
                    getLabel={(value) => editorConfig.possibleValues.find((p) => p.value === value)?.label || value}
                    isValid={(value) => editorConfig.possibleValues.some((p) => p.value === value)}
                />
            ),
            [editorConfig.possibleValues, removeByIndex],
        );

        return (
            <Box
                sx={{
                    display: "grid",
                    margin: "-1px", // FIXME: other fields have outline instead of border, so we need to compensate for that
                }}
            >
                <Select
                    multiple
                    value={values}
                    onChange={handleChange}
                    input={<OutlinedInput />}
                    renderValue={renderValue}
                    error={showValidation && fieldErrors.length > 0}
                    sx={{
                        borderRadius: 0,
                        minHeight: 35,
                        ".MuiOutlinedInput-input.MuiSelect-select": {
                            padding: 0,
                            paddingRight: 3,
                        },
                        "&, &.Mui-focused": {
                            ".MuiOutlinedInput-notchedOutline": {
                                borderWidth: 1,
                            },
                        },
                    }}
                >
                    {editorConfig.possibleValues.map(({ label, value }) => (
                        <MenuItem key={value} value={value}>
                            {label}
                        </MenuItem>
                    ))}
                </Select>
                {showValidation && <ValidationLabels fieldErrors={fieldErrors} />}
            </Box>
        );
    },
    {
        isSwitchableTo: ({ expression, language }, { possibleValues }) => {
            if (language !== ExpressionLang.JSON) return false;
            try {
                const values = JSON.parse(expression);
                if (Array.isArray(values)) {
                    return values.every((value) => possibleValues.find((v) => v.value === value));
                }
                return false;
            } catch {
                return false;
            }
        },
        notSwitchableToHint: () =>
            i18next.t(
                "editors.multiSelectFixedValues.notSwitchableToHint",
                "Expression must contain valid JSON with only predefined values to switch to {{editorName}} mode",
                { editorName: editorsParameters[EditorType.MULTI_SELECT_EDITOR].displayName },
            ),
    },
);
