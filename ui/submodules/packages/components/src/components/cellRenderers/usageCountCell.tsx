import React from "react";
import { GridRenderCellParams } from "@mui/x-data-grid";
import { Link as RouterLink } from "react-router-dom";
import { Link as LinkIcon } from "@mui/icons-material";
import { CellLink } from "./cellLink";

export function UsageCountCell(props: GridRenderCellParams): JSX.Element {
    return (
        <CellLink sx={{ fontWeight: "bold" }} color="primary" cellProps={props} component={RouterLink} to={`/${props.row.name}`}>
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
        </CellLink>
    );
}
