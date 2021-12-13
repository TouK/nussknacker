import { useFilterContext } from "../filters/filtersContext";
import React from "react";
import SearchIcon from "@mui/icons-material/Search";
import InputBase from "@mui/material/InputBase";
import Paper from "@mui/material/Paper";

export function Filters(): JSX.Element {
    const { getFilter, setFilter } = useFilterContext();
    return (
        <Paper sx={{ px: 1, pt: 0.5, flex: 1, display: "flex", alignItems: "center" }} elevation={0}>
            <SearchIcon fontSize="small" />
            <InputBase
                value={getFilter("TEXT") || ""}
                onChange={(e) => setFilter("TEXT", e.target.value)}
                sx={{ pl: 1, flex: 1 }}
                placeholder="Filter..."
                inputProps={{ "aria-label": "filter" }}
            />
        </Paper>
    );
}
