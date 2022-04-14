import { BoxProps, Box } from "@mui/material";
import React, { ElementType } from "react";

export const BoxWithArchivedStyle = <D extends ElementType>(props: BoxProps<D, { component: D; isArchived?: boolean }>): JSX.Element => {
    const { isArchived, component, ...passProps } = props;
    return (
        <Box
            sx={{
                "&& > *": isArchived && {
                    color: "warning.light",
                    opacity: 0.5,
                },
            }}
            component={component}
            {...passProps}
        />
    );
};
