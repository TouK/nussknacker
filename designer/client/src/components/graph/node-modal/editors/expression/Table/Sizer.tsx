import { Box } from "@mui/material";
import type { BoxProps } from "@mui/material/Box/Box";
import React, { useCallback, useRef } from "react";

import { useSize } from "../../../../../../containers/hooks/useSize";

type SizerProps = BoxProps & {
    overflowY?: boolean;
    offsetParent?: null | string | ((el?: HTMLElement) => HTMLElement);
};

const getOffsetParent: (el?: HTMLElement) => HTMLElement = (el?: HTMLElement): HTMLElement => {
    return el?.offsetParent as HTMLElement;
};

function getClosestParent(selector: string) {
    function closestParent(el: HTMLElement = document.body): HTMLElement | null {
        if (el?.nodeType !== 1) return null;
        return el.matches(selector) ? el : closestParent(el.parentElement);
    }
    return closestParent;
}

export function Sizer({ overflowY, offsetParent, ...props }: SizerProps) {
    const { observe, height, unobserve } = useSize();
    const ref = useRef<HTMLElement>(null);
    const refCallback = useCallback(
        (instance?: HTMLElement) => {
            unobserve();
            ref.current = instance;
            if (!offsetParent) return observe(getOffsetParent(instance));
            if (typeof offsetParent === "function") return observe(offsetParent(instance));
            return observe(getClosestParent(offsetParent)(instance));
        },
        [observe, offsetParent, unobserve],
    );

    ref.current?.style.setProperty("--height", `${height}px`);

    return (
        <Box
            {...props}
            sx={{
                "--maxHeight": `var(--sizer-maxHeight, calc(var(--height) * (var(--sizer-height-percent, 100) / 100) - var(--sizer-height-cutout, 200px)))`,
                "--minHeight": `var(--sizer-minHeight, 100px)`,
                overflow: "hidden",
                boxSizing: "border-box",
                minHeight: "var(--minHeight)",
                ...props.sx,
                flex: 1,
                position: "relative",
                maxHeight: overflowY ? "unset" : `max(var(--minHeight), var(--maxHeight))`,
            }}
            ref={refCallback}
        />
    );
}
