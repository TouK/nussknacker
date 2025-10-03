import { Box, styled } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { useRef, useState } from "react";
import { useInViewRef } from "rooks";

function equalSize(r1: DOMRectReadOnly, r2: DOMRectReadOnly): boolean {
    return r1.width === r2.width && r1.height === r2.height;
}

function getHorizontalRatio(entry: IntersectionObserverEntry) {
    return entry?.intersectionRect.width / entry?.boundingClientRect.width;
}

const Placeholder = styled("div")({
    paddingX: 0.75,
    paddingY: 0.5,
    whiteSpace: "nowrap",
    display: "flex",
    alignItems: "center",
});

export const VisibilitySensor = ({
    children,
    onChange,
}: PropsWithChildren<{
    onChange?: (ratio: number, size: DOMRectReadOnly) => void;
}>) => {
    const [maxSize, setMaxSize] = useState<DOMRectReadOnly>();
    const [ratio, setRatio] = useState(1);
    const intersection = useRef<IntersectionObserverEntry>();
    const [ref] = useInViewRef(
        ([entry]) => {
            const prevRatio = getHorizontalRatio(intersection.current);
            const ratio = getHorizontalRatio(entry);
            if (ratio < 1) setMaxSize(entry.boundingClientRect);
            if (prevRatio !== ratio || !equalSize(intersection.current?.intersectionRect, entry.intersectionRect)) {
                onChange?.(ratio, entry.intersectionRect);
            }
            setRatio(ratio);
            intersection.current = entry;
        },
        { threshold: 1 },
    );
    return (
        <Box sx={{ display: "flex" }}>
            {ratio >= 0.9 ? (
                <span ref={ref}>{children}</span>
            ) : (
                <Placeholder
                    ref={ref}
                    style={{
                        width: maxSize?.width,
                        height: maxSize?.height,
                    }}
                />
            )}
        </Box>
    );
};
