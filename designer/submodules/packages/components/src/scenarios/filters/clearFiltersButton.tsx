import { FilterListOff } from "@mui/icons-material";
import { IconButton } from "@mui/material";
import React from "react";

import type { SortableFiltersModel } from "./common/sortableFiltersModel";
import { useScenariosFilterContext } from "./common/useScenariosFilterContext";

export function ClearFiltersButton(): JSX.Element {
    const { getFilter, resetModel } = useScenariosFilterContext<SortableFiltersModel>();
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
