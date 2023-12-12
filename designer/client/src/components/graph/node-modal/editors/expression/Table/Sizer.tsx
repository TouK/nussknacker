import React, { useCallback } from "react";
import { useSize } from "../../../../../../containers/hooks/useSize";
import { Box } from "@mui/material";
import { BoxProps } from "@mui/material/Box/Box";

type SizerProps = BoxProps & {
    overflowY?: boolean;
};

export function Sizer({ overflowY, ...props }: SizerProps) {
    const { observe, height } = useSize();
    const callback = useCallback(
        (instance) => {
            observe(instance?.offsetParent.parentElement);
        },
        [observe],
    );

    return (
        <Box
            {...props}
            sx={{
                overflow: "hidden",
                boxSizing: "border-box",
                ...props.sx,
                flex: 1,
                position: "relative",
                minHeight: 100,
                maxHeight: overflowY ? "unset" : height * 0.8,
            }}
            ref={callback}
        />
    );
}
