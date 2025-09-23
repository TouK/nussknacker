import { Box } from "@mui/material";
import type { Scenario } from "nussknackerUi/components/Process/types";
import React from "react";

import { filterRules } from "../filters/filterRules";
import { ItemsList } from "./itemsList";

export interface RowType extends Scenario {
    hide?: boolean;
}

export interface ListPartProps<T> {
    data: T[];
    isLoading?: boolean;
}

export function ListPart(props: ListPartProps<RowType>): JSX.Element {
    const { data = [], isLoading } = props;
    return (
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
    );
}
