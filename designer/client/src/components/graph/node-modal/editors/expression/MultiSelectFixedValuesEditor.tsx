import type { CheckboxProps, SelectChangeEvent, TypographyProps } from "@mui/material";
import { Box, Checkbox, Chip, lighten, ListItemText, MenuItem, Select, Stack, Typography } from "@mui/material";
import i18next from "i18next";
import { groupBy, isEqual, uniq } from "lodash";
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ArrayElement } from "type-fest";

import ValidationLabels from "../../../../modals/ValidationLabels";
import { ValuesList } from "../../aggregate/groupBy/valuesList";
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

function ItemCheckbox(props: CheckboxProps) {
    return <Checkbox size="small" {...props} />;
}

function ItemLabel(props: TypographyProps) {
    return <Typography variant="body1" noWrap {...props} />;
}

function GroupItem({ item, values }: { item: { group: string; values: Option[] }; values: string[] }) {
    const included = item.values.filter((v) => values.includes(v));
    return (
        <>
            <ItemCheckbox
                checked={item.values.length === included.length}
                indeterminate={0 < included.length && included.length < item.values.length}
                color="default"
            />
            <ListItemText
                primary={
                    <Stack direction="row" gap={2} sx={{ justifyContent: "space-between" }}>
                        <ItemLabel>{item.group}</ItemLabel>
                        <ItemLabel
                            sx={(theme) => ({
                                color: theme.palette.action.disabled,
                            })}
                        >{`${included.length}/${item.values.length}`}</ItemLabel>
                    </Stack>
                }
            />
        </>
    );
}

function ElementItem({ item, values }: { item: Option; values: string[] }) {
    return (
        <>
            <ItemCheckbox checked={values.includes(item.value)} />
            <ListItemText
                primary={<ItemLabel>{item.label}</ItemLabel>}
                secondary={
                    <Typography variant="overline" component={LineClamp} lines={2}>
                        {item.description}
                    </Typography>
                }
            />
        </>
    );
}

export const MultiSelectFixedValuesEditor = prepareEditor<{ editorConfig: EditorConfigForType<EditorType.MULTI_SELECT_EDITOR> }>(
    ({ editorConfig, expressionObj, onValueChange, showValidation, fieldErrors, defaultValue }) => {
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
            ({ target }: SelectChangeEvent<string[]>) => {
                emit(Array.isArray(target.value) ? target.value.filter(Boolean) : []);
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

        const fletGroups = useMemo(
            () =>
                Object.entries(groupBy(editorConfig.possibleValues, (e) => e.group)).flatMap(([group, values], index, groups) => {
                    if (groups.length > 1 && group.length > 0) return [{ group, values: values.map((v) => v.value) }, ...values];
                    return values;
                }),
            [editorConfig.possibleValues],
        );

        const [menuWidth, setMenuWidth] = useState(null);
        const ref = useRef(null);
        const onOpen = useCallback(() => {
            if (ref.current) setMenuWidth(ref.current.clientWidth);
        }, []);

        return (
            <Box sx={{ display: "grid", flex: 1 }}>
                <Select
                    variant="outlined"
                    multiple
                    value={values}
                    onChange={handleChange}
                    renderValue={renderValue}
                    error={showValidation && fieldErrors.length > 0}
                    sx={{ minHeight: 35 }}
                    MenuProps={{
                        slotProps: {
                            paper: {
                                sx: {
                                    maxWidth: menuWidth,
                                    "& .MuiMenuItem-root": {
                                        whiteSpace: "normal",
                                        wordBreak: "break-word",
                                    },
                                },
                            },
                        },
                    }}
                    ref={ref}
                    onOpen={onOpen}
                >
                    {fletGroups.map((item) =>
                        "value" in item ? (
                            <MenuItem key={item.value} value={item.value}>
                                <ElementItem item={item} values={values} />
                            </MenuItem>
                        ) : (
                            <MenuItem
                                dense
                                sx={(theme) => ({
                                    position: "sticky",
                                    top: 0,
                                    zIndex: 1,
                                    background: lighten(theme.palette.background.paper, 0.075),
                                    "&:hover, &:focus": {
                                        background: theme.palette.background.paper,
                                    },
                                })}
                                onKeyDownCapture={(event) => {
                                    if (event.key !== "Enter") return;
                                    event.stopPropagation();
                                    toggleValues(item.values);
                                }}
                                onClickCapture={(event) => {
                                    event.stopPropagation();
                                    toggleValues(item.values);
                                }}
                            >
                                <GroupItem item={item} values={values} />
                            </MenuItem>
                        ),
                    )}
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
