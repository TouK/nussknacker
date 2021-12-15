import { Link as LinkIcon } from "@mui/icons-material";
import React from "react";
import { GridRenderCellParams } from "@mui/x-data-grid";
import { Link as RouterLink } from "react-router-dom";
import { Box } from "@mui/material";
import { CellLink } from "./cellLink";

export function UsageCountCell(props: GridRenderCellParams): JSX.Element {
    return (
        <CellLink
            sx={{ fontWeight: "bold" }}
            disabled={!props.value}
            color="primary"
            cellProps={props}
            component={RouterLink}
            to={`/usages/${props.row.id}`}
        >
            {!props.value ? (
                <Box
                    sx={{
                        fontWeight: "light",
                        opacity: 0.25,
                    }}
                >
                    {props.value}
                </Box>
            ) : (
                <>
                    <LinkIcon
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
                    {props.value}
                </>
            )}
        </CellLink>
    );
}
