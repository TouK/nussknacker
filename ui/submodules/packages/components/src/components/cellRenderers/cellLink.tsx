import React, { PropsWithChildren } from "react";
import { GridRenderCellParams } from "@mui/x-data-grid";
import { Box, Link, LinkProps, SxProps } from "@mui/material";
import { useCellArrowKeys } from "./useCellArrowKeys";

type CellLinkProps<C extends React.ElementType> = LinkProps<C, { component?: C }> &
    PropsWithChildren<{
        cellProps: GridRenderCellParams;
        disabled?: boolean;
    }>;


export function CellLink<C extends React.ElementType>(props: CellLinkProps<C>): JSX.Element {
    const { cellProps, disabled, children, sx, ...passProps } = props;
    const handleCellKeyDown = useCellArrowKeys(cellProps);

    const styles: SxProps<any> = {
        display: "flex",
        textAlign: "inherit",
        justifyContent: "inherit",
        alignItems: "center",
        ...sx,
    };

    return disabled ? (
        <Box px="10px" sx={styles}>
            <span>{children}</span>
        </Box>
    ) : (
        <Link
            color="inherit"
            sx={{
                flex: 1,
                ":focus": {
                    outline: "unset",
                },
                ...styles,
            }}
            tabIndex={0}
            onKeyDown={handleCellKeyDown}
            {...passProps}
        >
            <Box px="10px">{children}</Box>
        </Link>
    );
}
