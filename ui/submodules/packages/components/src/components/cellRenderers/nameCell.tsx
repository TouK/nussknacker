import Highlighter from "react-highlight-words";
import React, { PropsWithChildren } from "react";
import { GridRenderCellParams } from "@mui/x-data-grid";
import { Box } from "@mui/material";
import { CellLink } from "./cellLink";
import { OpenInNew } from "@mui/icons-material";
import { scenarioHref } from "./categoriesCell";
import { useFilterContext } from "../filters/filtersContext";
import { IconImg } from "./iconImg";

export function NameCell(props: GridRenderCellParams): JSX.Element {
    const { value, row } = props;
    const { getFilter } = useFilterContext();
    const children = (
        <>
            <IconImg title={row.componentType} src={row.icon} />{" "}
            <Highlighter textToHighlight={value.toString()} searchWords={getFilter("NAME", true)} highlightTag={Highlight} />
        </>
    );
    const isFragment = row.componentGroupName === "fragments";
    return (
        <CellLink underline="hover" disabled={!isFragment} color="inherit" cellProps={props} href={scenarioHref(value)}>
            {isFragment ? (
                <>
                    {children}
                    <OpenInNew
                        sx={{
                            height: ".75em",
                            margin: ".25em",
                            verticalAlign: "middle",
                            opacity: 0.1,
                            "a:hover &": {
                                opacity: 0.5,
                            },
                            "a:focus &": {
                                opacity: 0.5,
                            },
                        }}
                    />
                </>
            ) : (
                children
            )}
        </CellLink>
    );
}

export function Highlight({ children }: PropsWithChildren<unknown>): JSX.Element {
    return (
        <Box component="span" sx={{ color: "primary.main" }}>
            {children}
        </Box>
    );
}
