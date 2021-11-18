import { Box, Checkbox, FormControlLabel, Grid, TextField } from "@mui/material";
import React, { PropsWithChildren, useCallback } from "react";
import { SelectFilter } from "./selectFilter";
import { useTranslation } from "react-i18next";

interface FilterType<V extends any, R extends Record<string, any> = any> {
    id: string;
    check?: (row: R, value: V) => boolean;
    value?: V;
}

export type FilterModel = FilterType<unknown>[];

export interface FiltersProps {
    model: FilterModel;
    onFilterChange: (value: (current: FilterModel) => FilterModel) => void;

    values: Record<string, string[]>;
}

export function Filters(props: PropsWithChildren<FiltersProps>): JSX.Element {
    const { values, model, onFilterChange } = props;
    const { t } = useTranslation();

    const setFilter = useCallback(
        <T extends any>(filter: FilterType<T>) =>
            onFilterChange((model: FilterModel) => [...model.filter((m) => m.id !== filter.id), filter].filter(({ value }) => !!value)),
        [onFilterChange],
    );
    const getFilterValue = useCallback((name: string) => model.find(({ id }) => id === name)?.value, [model]);

    return (
        <>
            <Grid
                component={"form"}
                noValidate
                autoComplete="off"
                container
                alignItems="center"
                spacing={2}
                direction="row"
                justifyContent="flex-end"
            >
                <Grid item xs={12} md={4} lg xl>
                    <TextField
                        label={t("table.filter.NAME", "Name")}
                        variant="outlined"
                        fullWidth
                        value={getFilterValue("NAME")}
                        onChange={(e) =>
                            setFilter({
                                id: "NAME",
                                value: e.target.value.toLowerCase(),
                                check: (row, value) => !value.length || row["name"]?.toLowerCase().includes(value),
                            })
                        }
                    />
                </Grid>
                <Grid item xs={12} md lg={3} xl>
                    <SelectFilter
                        label={t("table.filter.GROUP", "Group")}
                        options={values["componentGroupName"]}
                        value={[].concat(getFilterValue("GROUP")).filter(Boolean)}
                        onChange={(value) =>
                            setFilter({
                                id: "GROUP",
                                value,
                                check: (row, value = []) => !value.length || value.some((f) => row["componentGroupName"]?.includes(f)),
                            })
                        }
                    />
                </Grid>
                <Grid item xs={12} md lg={3} xl>
                    <SelectFilter
                        label={t("table.filter.CATEGORY", "Category")}
                        options={values["categories"]}
                        value={[].concat(getFilterValue("CATEGORY")).filter(Boolean)}
                        onChange={(value) =>
                            setFilter({
                                id: "CATEGORY",
                                value,
                                check: (row, value = []) => !value.length || value.some((f) => row["categories"]?.includes(f)),
                            })
                        }
                    />
                </Grid>
                <Grid item>
                    <Box sx={{ display: "flex", flexWrap: "wrap", columnGap: 2 }}>
                        <FormControlLabel
                            control={
                                <Checkbox
                                    checked={getFilterValue("UNUSED_ONLY") === true}
                                    onChange={(e) => {
                                        setFilter({
                                            id: "UNUSED_ONLY",
                                            check: (row) => row["usageCount"] === 0,
                                            value: e.target.checked,
                                        });
                                        setFilter({
                                            id: "USED_ONLY",
                                        });
                                    }}
                                />
                            }
                            label={t("table.filter.UNUSED_ONLY", "Show unused only")}
                        />
                        <FormControlLabel
                            control={
                                <Checkbox
                                    checked={getFilterValue("USED_ONLY") === true}
                                    onChange={(e) => {
                                        setFilter({
                                            id: "USED_ONLY",
                                            check: (row) => row["usageCount"] > 0,
                                            value: e.target.checked,
                                        });
                                        setFilter({
                                            id: "UNUSED_ONLY",
                                        });
                                    }}
                                />
                            }
                            label={t("table.filter.USED_ONLY", "Show used only")}
                        />
                    </Box>
                </Grid>
            </Grid>
        </>
    );
}
