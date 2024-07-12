import React, { useCallback } from "react";
import { useSize } from "../../../../../../containers/hooks/useSize";
import { Box } from "@mui/material";
import { BoxProps } from "@mui/material/Box/Box";

type SizerProps = BoxProps & {
    overflowY?: boolean;
    offsetParent?: null | string | ((el?: HTMLElement) => HTMLElement);
};

const getOffsetParent: (el?: HTMLElement) => HTMLElement = (el?: HTMLElement): HTMLElement => {
    return el?.offsetParent as HTMLElement;
};

export function Sizer({ overflowY, offsetParent, ...props }: SizerProps) {
    const { observe, height, unobserve } = useSize();
    const refCallback = useCallback(
        (instance?: HTMLElement) => {
            unobserve();
            if (!offsetParent) return observe(getOffsetParent(instance));
            if (typeof offsetParent === "function") return observe(offsetParent(instance));
            observe(document.querySelector(offsetParent));
        },
        [observe, offsetParent, unobserve],
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
