import type { CheckboxProps, TypographyProps } from "@mui/material";
import { Autocomplete, Box, Checkbox, Chip, ListItemText, MenuItem, Stack, TextField, Typography } from "@mui/material";
import { createFilterOptions } from "@mui/material/Autocomplete";
import i18next from "i18next";
import { isEqual, uniq } from "lodash";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import type { ArrayElement } from "type-fest";

import ValidationLabels from "../../../../modals/ValidationLabels";
import { SearchHighlighter } from "../../../../toolbars/creator/SearchHighlighter";
import { useStream } from "../../node/useStream";
import { prepareEditor } from "./Editor";
import type { EditorConfigForType } from "./EditorConfig";
import { editorsParameters } from "./editorsParameters";
import { LineClamp } from "./LineClamp";
import { EditorType, ExpressionLang } from "./types";

function getExpressionFromValues(values: string[]) {
    return {
        language: editorsParameters[EditorType.MULTI_SELECT_EDITOR].language,
        expression: JSON.stringify(values),
    };
}

type Option = ArrayElement<EditorConfigForType<EditorType.MULTI_SELECT_EDITOR>["possibleValues"]>;

const filterOptions = createFilterOptions({
    matchFrom: "any",
    ignoreAccents: true,
    ignoreCase: true,
    stringify: (option: Option) => `${option.label} ${option.description}`,
});

function ItemCheckbox(props: CheckboxProps) {
    return <Checkbox size="small" disableRipple {...props} />;
}

function ItemLabel(props: TypographyProps) {
    return <Typography variant="body1" noWrap {...props} />;
}

export const MultiSelectFixedValuesEditor = prepareEditor<{ editorConfig: EditorConfigForType<EditorType.MULTI_SELECT_EDITOR> }>(
    ({ editorConfig, expressionObj, onValueChange, showValidation, fieldErrors, defaultValue }) => {
        const [input, setInput] = useState<string>("");
        const [value$, emit, values] = useStream<string[]>(() => {
            try {
                const parsed = JSON.parse(expressionObj.expression);
                if (Array.isArray(parsed)) return parsed;
                return JSON.parse(typeof defaultValue === "string" ? defaultValue : defaultValue.expression);
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
            (_, nextValue: Option[]) => {
                emit(nextValue.map((option) => option.value));
            },
            [emit],
        );

        const toggleValues = useCallback(
            (values: string[]) => {
                emit((current) =>
                    values.every((v) => current.includes(v)) ? current.filter((v) => !values.includes(v)) : uniq([...current, ...values]),
                );
            },
            [emit],
        );

        const selectedOptions = useMemo(() => {
            return values.map((value) => editorConfig.possibleValues.find((v) => v.value === value) || { value });
        }, [editorConfig.possibleValues, values]);

        const groups = useMemo(() => uniq(editorConfig.possibleValues.map((v) => v.group)), [editorConfig.possibleValues]);

        return (
            <Box sx={{ display: "grid", flex: 1 }}>
                <Autocomplete
                    multiple
                    disableCloseOnSelect
                    options={editorConfig.possibleValues}
                    value={selectedOptions}
                    onChange={handleChange}
                    onInputChange={(_, input) => setInput(input)}
                    isOptionEqualToValue={(option, value) => option.value === value.value}
                    getOptionLabel={({ label, value }) => label || value}
                    groupBy={groups.length > 1 ? (option) => option.group : undefined}
                    filterOptions={filterOptions}
                    autoHighlight
                    renderInput={(params) => <TextField {...params} variant="outlined" error={showValidation && fieldErrors.length > 0} />}
                    renderOption={(props, option, { selected }) => (
                        <li {...props}>
                            <ItemCheckbox checked={selected} />
                            <ListItemText
                                primary={
                                    <ItemLabel>
                                        <SearchHighlighter highlights={[input]}>{option.label}</SearchHighlighter>
                                    </ItemLabel>
                                }
                                secondary={
                                    <Typography variant="overline" component={LineClamp} lines={2}>
                                        <SearchHighlighter
                                            highlights={[input]}
                                            sx={{
                                                ".Highlighter-highlight": {
                                                    fontWeight: "inherit",
                                                },
                                            }}
                                        >
                                            {option.description}
                                        </SearchHighlighter>
                                    </Typography>
                                }
                            />
                        </li>
                    )}
                    renderTags={(values, getItemProps) =>
                        values.map((option, index) => {
                            const { key, ...itemProps } = getItemProps({ index });
                            return (
                                <Chip
                                    key={key}
                                    {...itemProps}
                                    label={option.label || option.value}
                                    color={editorConfig.possibleValues.some((p) => p.value === option.value) ? "default" : "error"}
                                    size="small"
                                    variant="outlined"
                                />
                            );
                        })
                    }
                    renderGroup={(params) => {
                        const allOptionsInGroup = editorConfig.possibleValues.filter((option) => option.group === params.group);
                        const allValuesInGroup = allOptionsInGroup.map((option) => option.value);
                        const selectedCount = allValuesInGroup.filter((v) => values.includes(v)).length;
                        return (
                            <li key={params.key}>
                                <MenuItem
                                    dense
                                    sx={(theme) => ({
                                        position: "sticky",
                                        top: -8,
                                        zIndex: 1,
                                        "&, &:hover": {
                                            background: theme.palette.background.paper,
                                        },
                                    })}
                                    onClick={() => toggleValues(allValuesInGroup)}
                                >
                                    <ItemCheckbox
                                        checked={allValuesInGroup.length > 0 && selectedCount === allValuesInGroup.length}
                                        indeterminate={selectedCount > 0 && selectedCount < allValuesInGroup.length}
                                        disabled //for color only
                                    />
                                    <ListItemText
                                        primary={
                                            <Stack direction="row" gap={1} sx={{ justifyContent: "space-between", flex: 1 }}>
                                                <ItemLabel>{params.group || i18next.t("common.ungrouped", "Ungrouped")}</ItemLabel>
                                                <ItemLabel
                                                    sx={(theme) => ({
                                                        color: theme.palette.action.disabled,
                                                    })}
                                                >{`${selectedCount}/${allValuesInGroup.length}`}</ItemLabel>
                                            </Stack>
                                        }
                                    />
                                </MenuItem>
                                {params.children}
                            </li>
                        );
                    }}
                    sx={{
                        margin: "-1px", // FIXME: other fields have outline instead of border, so we need to compensate for that
                        ".MuiAutocomplete-inputRoot": {
                            borderRadius: 0,
                            "&, &.Mui-focused": {
                                ".MuiOutlinedInput-notchedOutline": {
                                    borderWidth: 1,
                                },
                            },
                            paddingLeft: 0,
                            paddingY: "3.5px",
                            ".MuiAutocomplete-input": {
                                fontSize: "14px",
                                height: "1.5em",
                                paddingY: 0,
                                "&:first-child": {
                                    paddingY: "4.5px",
                                    marginLeft: 1.25,
                                },
                            },
                        },
                    }}
                />
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
