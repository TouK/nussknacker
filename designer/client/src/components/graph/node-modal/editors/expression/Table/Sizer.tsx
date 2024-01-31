import React, { useCallback } from "react";
import { useSize } from "../../../../../../containers/hooks/useSize";
import { Box } from "@mui/material";
import { BoxProps } from "@mui/material/Box/Box";

type SizerProps = BoxProps & {
    overflowY?: boolean;
    offsetParent?: string | ((el?: HTMLElement) => HTMLElement);
};

const getOffsetParent: (el?: HTMLElement) => HTMLElement = (el?: HTMLElement): HTMLElement => {
    return el?.offsetParent as HTMLElement;
};

export function Sizer({ overflowY, offsetParent = getOffsetParent, ...props }: SizerProps) {
    const { observe, height } = useSize();
    const refCallback = useCallback(
        (instance?: HTMLElement) => {
            let el: HTMLElement;
            if (typeof offsetParent === "string") {
                el = document.querySelector(offsetParent);
            } else {
                el = offsetParent(instance);
            }
            observe(el);
        },
        [observe, offsetParent],
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
            ref={refCallback}
        />
    );
}
