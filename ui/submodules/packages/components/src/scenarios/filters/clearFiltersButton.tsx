import { useFilterContext } from "../../common";
import { ScenariosFiltersModel } from "./scenariosFiltersModel";
import React from "react";
import { IconButton } from "@mui/material";
import { FilterListOff } from "@mui/icons-material";

export function ClearFiltersButton(): JSX.Element {
    const { getFilter, resetModel } = useFilterContext<ScenariosFiltersModel>();
    return (
        <IconButton
            size="small"
            sx={{
                padding: 0,
                color: (t) => t.palette.getContrastText(t.palette.background.default),
            }}
            onClick={() => {
                const SORT_BY = getFilter("SORT_BY");
                resetModel(SORT_BY ? { SORT_BY } : {});
            }}
        >
            <FilterListOff color="inherit" />
        </IconButton>
    );
}
