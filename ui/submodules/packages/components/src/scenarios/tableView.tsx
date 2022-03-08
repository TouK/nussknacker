import React from "react";
import { filterRules, FiltersRow } from "./filters";
import { ItemsList } from "./list/itemsList";
import { ProcessType } from "nussknackerUi/components/Process/types";
import { Box } from "@mui/material";

export type RowType = ProcessType;

export interface TableViewData<T> {
    data: T[];
    isLoading?: boolean;
}

export function TableView(props: TableViewData<RowType>): JSX.Element {
    const { data = [], isLoading } = props;
    return (
        <>
            <FiltersRow {...props} />

            <Box sx={{ display: "flex", alignItems: "flex-start", flexDirection: "row-reverse" }}>
                <Box
                    sx={{
                        flex: 1,
                        width: 0,
                        overflow: "hidden",
                    }}
                >
                    <ItemsList data={data} filterRules={filterRules} isLoading={isLoading} />
                </Box>
            </Box>
        </>
    );
}
