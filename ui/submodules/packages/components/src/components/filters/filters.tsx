import { Box, Checkbox, FormControlLabel, Grid } from "@mui/material";
import React, { PropsWithChildren, useCallback, useMemo } from "react";
import { SelectFilter } from "../selectFilter";
import { useTranslation } from "react-i18next";
import { useFilterContext } from "../../common/filters";
import { TextFieldWithClear } from "../../common";
import { ComponentsFiltersModel } from "./componentsFiltersModel";

export interface FiltersProps {
    values: Record<string, string[]>;
}

export function Filters(props: PropsWithChildren<FiltersProps>): JSX.Element {
    const { values } = props;
    const { getFilter, setFilter, setFilterImmediately } = useFilterContext<ComponentsFiltersModel>();
    const { t } = useTranslation();

    const setNameFilter = useMemo(() => setFilterImmediately("NAME"), [setFilterImmediately]);
    const setGroupFilter = useMemo(() => setFilter("GROUP"), [setFilter]);
    const setCategoryFilter = useMemo(() => setFilter("CATEGORY"), [setFilter]);

    const setUnusedOnlyFilter = useCallback(
        (e) => {
            setFilter("UNUSED_ONLY", e.target.checked);
        },
        [setFilter],
    );

    const setUsedOnlyFilter = useCallback(
        (e) => {
            setFilter("USED_ONLY", e.target.checked);
        },
        [setFilter],
    );

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
                <Grid item xs={12} md>
                    <TextFieldWithClear
                        label={t("table.filter.NAME", "Name")}
                        variant="filled"
                        fullWidth
                        value={getFilter("NAME") || ""}
                        onChange={setNameFilter}
                    />
                </Grid>
                <Grid item xs={12} sm={6} md={4} lg={3} xl={3}>
                    <SelectFilter
                        label={t("table.filter.GROUP", "Group")}
                        options={values["componentGroupName"]}
                        value={getFilter("GROUP", true)}
                        onChange={setGroupFilter}
                    />
                </Grid>
                <Grid item xs={12} sm={6} md={4} lg={3} xl={3}>
                    <SelectFilter
                        label={t("table.filter.CATEGORY", "Category")}
                        options={values["categories"]}
                        value={getFilter("CATEGORY", true)}
                        onChange={setCategoryFilter}
                    />
                </Grid>
                <Grid item xl>
                    <Box sx={{ display: "flex", whiteSpace: "nowrap", justifyContent: "flex-end", columnGap: 2, ml: 2 }}>
                        <FormControlLabel
                            control={<Checkbox checked={getFilter("UNUSED_ONLY") === true} onChange={setUnusedOnlyFilter} />}
                            label={`${t("table.filter.UNUSED_ONLY", "Show unused only")}`}
                        />
                        <FormControlLabel
                            control={<Checkbox checked={getFilter("USED_ONLY") === true} onChange={setUsedOnlyFilter} />}
                            label={`${t("table.filter.USED_ONLY", "Show used only")}`}
                        />
                    </Box>
                </Grid>
            </Grid>
        </>
    );
}
