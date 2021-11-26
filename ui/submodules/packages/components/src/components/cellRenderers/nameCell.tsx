import Highlighter from "react-highlight-words";
import React, { PropsWithChildren } from "react";
import { GridRenderCellParams } from "@mui/x-data-grid";
import { Box } from "@mui/material";

export function NameCell({ value, row, filterValue }: GridRenderCellParams & { filterValue: string[] }): JSX.Element {
    return (
        <>
            <img title={row.componentType} style={{ height: "1.5em", marginRight: ".25em" }} src={row.icon} />
            <Highlighter textToHighlight={value.toString()} searchWords={filterValue} highlightTag={Highlight} />
        </>
    );
}

function Highlight({ children }: PropsWithChildren<unknown>): JSX.Element {
    return (
        <Box component="span" sx={{ color: "primary.main" }}>
            {children}
        </Box>
    );
}
