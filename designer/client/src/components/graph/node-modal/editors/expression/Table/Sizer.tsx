import React, { PropsWithChildren, useCallback } from "react";
import { useSize } from "../../../../../../containers/hooks/useSize";
import { Box } from "@mui/material";

type SizerProps = PropsWithChildren<{
    overflowY?: boolean;
}>;

export function Sizer({ children, overflowY }: SizerProps) {
    const { observe, height } = useSize();
    const callback = useCallback(
        (instance) => {
            observe(instance?.offsetParent.parentElement);
        },
        [observe],
    );

    return (
        <Box
            sx={{
                flex: 1,
                overflow: "hidden",
                resize: "vertical",
                position: "relative",
                minHeight: 150,
                maxHeight: overflowY ? "unset" : height * 0.8,
            }}
            ref={callback}
        >
            {children}
        </Box>
    );
}
