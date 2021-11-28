import { Box, Checkbox, FormControlLabel, Grid, TextField } from "@mui/material";
import React, { PropsWithChildren } from "react";
import { SelectFilter } from "../selectFilter";
import { useTranslation } from "react-i18next";
import { useFilterContext } from "./filtersContext";

export interface FiltersProps {
    values: Record<string, string[]>;
}

export function Filters(props: PropsWithChildren<FiltersProps>): JSX.Element {
    const { values } = props;
    const { getFilter, setFilter } = useFilterContext();
    const { t } = useTranslation();

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
                        variant="filled"
                        fullWidth
                        value={getFilter("NAME") || ""}
                        onChange={(e) => setFilter("NAME", e.target.value.toLowerCase())}
                    />
                </Grid>
                <Grid item xs={12} md lg={3} xl>
                    <SelectFilter
                        label={t("table.filter.GROUP", "Group")}
                        options={values["componentGroupName"]}
                        value={getFilter("GROUP", true)}
                        onChange={(value) => setFilter("GROUP", value)}
                    />
                </Grid>
                <Grid item xs={12} md lg={3} xl>
                    <SelectFilter
                        label={t("table.filter.CATEGORY", "Category")}
                        options={values["categories"]}
                        value={getFilter("CATEGORY", true)}
                        onChange={(value) => setFilter("CATEGORY", value)}
                    />
                </Grid>
                <Grid item>
                    <Box sx={{ display: "flex", flexWrap: "wrap", columnGap: 2 }}>
                        <FormControlLabel
                            control={
                                <Checkbox
                                    checked={getFilter("UNUSED_ONLY") === true}
                                    onChange={(e) => {
                                        setFilter("UNUSED_ONLY", e.target.checked);
                                        setFilter("USED_ONLY", null);
                                    }}
                                />
                            }
                            label={t("table.filter.UNUSED_ONLY", "Show unused only")}
                        />
                        <FormControlLabel
                            control={
                                <Checkbox
                                    checked={getFilter("USED_ONLY") === true}
                                    onChange={(e) => {
                                        setFilter("USED_ONLY", e.target.checked);
                                        setFilter("UNUSED_ONLY", null);
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
