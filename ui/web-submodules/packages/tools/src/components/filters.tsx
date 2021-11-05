import { Box, FormControlLabel, Grid, Switch, TextField } from "@mui/material";
import React, { PropsWithChildren, useCallback } from "react";
import { SelectFilter } from "./selectFilter";

export type FilterModel = Array<{
    column: string;
    value: string;
}>;

export interface FiltersProps {
    model: FilterModel;
    onFilterChange: (value: (current: FilterModel) => FilterModel) => void;

    values: Record<string, string[]>;
}
const label = { inputProps: { "aria-label": "Switch demo" } };

export function Filters(props: PropsWithChildren<FiltersProps>): JSX.Element {
    const { values, model, onFilterChange } = props;

    const setFilter = useCallback(
        (column: string, value: string) =>
            onFilterChange((model: FilterModel) => [...model.filter((m) => m.column !== column), { column, value }]),
        [onFilterChange],
    );

    const getValue = useCallback((name: string) => model.find(({ column }) => column === name)?.value || "", [model]);

    return (
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
            <Grid item xs={12} md={5} lg xl>
                <TextField
                    label="Name"
                    variant="outlined"
                    fullWidth
                    value={getValue("id")}
                    onChange={(e) => setFilter("id", e.target.value)}
                />
            </Grid>
            <Grid item xs={12} md lg={3} xl>
                <SelectFilter
                    label="Type"
                    options={values["type"]}
                    value={getValue("type")}
                    onChange={(value) => setFilter("type", value)}
                />
            </Grid>
            <Grid item xs={12} md lg={3} xl>
                <SelectFilter
                    label="Categories"
                    options={values["categories"]}
                    value={getValue("categories")}
                    onChange={(value) => setFilter("categories", value)}
                />
            </Grid>
            <Grid item>
                <Box sx={{ display: "flex", flexWrap: "wrap", columnGap: 2 }}>
                    <FormControlLabel control={<Switch />} label="Show only used" />
                    <FormControlLabel control={<Switch />} label="Show only invokable" />
                </Box>
            </Grid>
        </Grid>
    );
}
