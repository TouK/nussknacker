import { Box } from "@mui/material";
import type { PropsWithChildren } from "react";
import React from "react";

import { WithSeparator } from "./withSeparator";

export function SecondaryElementsRow({ children }: PropsWithChildren) {
    const nodes = React.Children.toArray(children);
    return nodes.length > 0 ? (
        <Box sx={{ display: "flex", flexWrap: "wrap", justifyContent: "flex-end" }}>
            {nodes.map((child, i, array) => (
                <WithSeparator key={i} showDivider={i < array.length - 1}>
                    {child}
                </WithSeparator>
            ))}
        </Box>
    ) : null;
}
