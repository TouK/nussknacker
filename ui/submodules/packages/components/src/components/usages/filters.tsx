import { useFilterContext } from "../../common";
import React, { ChangeEvent, useCallback, useMemo } from "react";
import SearchIcon from "@mui/icons-material/Search";
import InputBase from "@mui/material/InputBase";
import Paper from "@mui/material/Paper";
import { useTranslation } from "react-i18next";
import { IconButton, InputAdornment } from "@mui/material";
import ClearIcon from "@mui/icons-material/Clear";
import { UsagesFiltersModel } from "./usagesFiltersModel";

export function Filters(): JSX.Element {
    const { t } = useTranslation();
    const { getFilter, setFilterImmediately } = useFilterContext<UsagesFiltersModel>();
    const setText = useMemo(() => setFilterImmediately("TEXT"), [setFilterImmediately]);
    const onChange = useCallback((e: ChangeEvent<HTMLInputElement>) => setText(e.target.value), [setText]);
    const reset = useCallback(() => setText(null), [setText]);
    const preventDefault = useCallback((event) => event.preventDefault(), []);
    const value = useMemo(() => getFilter("TEXT") || "", [getFilter]);
    return (
        <Paper sx={{ px: 1.5, py: 1, flex: 1, display: "flex", alignItems: "center" }} elevation={0}>
            <SearchIcon fontSize="small" />
            <InputBase
                value={value}
                onChange={onChange}
                sx={{ pl: 1, flex: 1 }}
                placeholder={t("table.filter.QUICK", "Search...")}
                inputProps={{
                    "aria-label": "filter",
                    style: { padding: 0 },
                }}
                endAdornment={
                    value && (
                        <InputAdornment position="end">
                            <IconButton aria-label="clear" onClick={reset} onMouseDown={preventDefault} edge="end">
                                <ClearIcon />
                            </IconButton>
                        </InputAdornment>
                    )
                }
            />
        </Paper>
    );
}
