import { Box } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { useState } from "react";
import { useResizeObserverRef } from "rooks";

type ScaleProps = PropsWithChildren<{
    zoom?: number;
    origin?: "left" | "right";
}>;

function PlainZoom({ children, zoom = 1 }: ScaleProps) {
    return <span style={{ zoom }}>{children}</span>;
}

function ZoomWorkaround({ children, zoom = 1, origin = "left" }: ScaleProps) {
    const [height, setHeight] = useState<number>(null);
    const [ref] = useResizeObserverRef(([entry]) => {
        const boundingClientRect = entry.target.getBoundingClientRect();
        setHeight(boundingClientRect.height);
    });

    return (
        <Box style={{ height }}>
            <Box ref={ref} style={{ transform: `scale(${zoom})`, transformOrigin: `top ${origin}` }}>
                {children}
            </Box>
        </Box>
    );
}

const isSafari = (): boolean => {
    const ua = navigator.userAgent;
    const vendor = navigator.vendor ?? "";
    return /Safari/.test(ua) && /Apple Computer/.test(vendor);
};

export default isSafari() ? ZoomWorkaround : PlainZoom;
