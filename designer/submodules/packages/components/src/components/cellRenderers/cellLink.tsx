import React, { PropsWithChildren } from "react";
import { Box, Link, LinkProps, SxProps } from "@mui/material";

type CellLinkProps<C extends React.ElementType> = LinkProps<C, { component?: C }> &
    PropsWithChildren<{
        disabled?: boolean;
    }>;

export function CellLink<C extends React.ElementType>(props: CellLinkProps<C>): JSX.Element {
    const { disabled, children, sx, ...passProps } = props;

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
            {...passProps}
        >
            <Box px="10px">{children}</Box>
        </Link>
    );
}
