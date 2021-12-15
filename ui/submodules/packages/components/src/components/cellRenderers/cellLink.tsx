import React from "react";
import { GridRenderCellParams } from "@mui/x-data-grid";
import { Box, Link, LinkProps } from "@mui/material";
import { useCellArrowKeys } from "./useCellArrowKeys";

export function CellLink<C extends React.ElementType>(
    props: { cellProps: GridRenderCellParams; disabled?: boolean } & LinkProps<C, { component?: C }>,
): JSX.Element {
    const { cellProps, disabled, children, sx, ...passProps } = props;
    const handleCellKeyDown = useCellArrowKeys(cellProps);

    const box = <Box px="10px">{children}</Box>;

    return disabled ? (
        box
    ) : (
        <Link
            color="inherit"
            sx={{
                flex: 1,
                textAlign: "inherit",
                ":focus": {
                    outline: "unset",
                },
                ...sx,
            }}
            tabIndex={0}
            onKeyDown={handleCellKeyDown}
            {...passProps}
        >
            {box}
        </Link>
    );
}
