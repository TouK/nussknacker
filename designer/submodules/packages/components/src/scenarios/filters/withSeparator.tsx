import { Box, Divider } from "@mui/material";
import type { PropsWithChildren, ReactNode } from "react";
import React, { useRef, useState } from "react";
import { useInViewRef } from "rooks";

function equalSize(r1: DOMRectReadOnly, r2: DOMRectReadOnly): boolean {
    return r1.width === r2.width && r1.height === r2.height;
}

function getRatio(entry: IntersectionObserverEntry) {
    return entry?.intersectionRect.width / entry?.boundingClientRect.width;
}

export const WithSeparator = ({
    children,
    showDivider,
    onVisibilityChange,
    expandButton,
}: PropsWithChildren<{
    expandButton?: ReactNode;
    showDivider?: boolean;
    onVisibilityChange?: (ratio: number, size: DOMRectReadOnly) => void;
}>) => {
    const [maxSize, setMaxSize] = useState<DOMRectReadOnly>();
    const [ratio, setRatio] = useState(1);
    const intersection = useRef<IntersectionObserverEntry>();
    const [ref] = useInViewRef(
        ([entry]) => {
            const prevRatio = getRatio(intersection.current);
            const ratio = getRatio(entry);
            if (ratio < 1) setMaxSize(entry.boundingClientRect);
            if (prevRatio !== ratio || !equalSize(intersection.current?.intersectionRect, entry.intersectionRect)) {
                onVisibilityChange?.(ratio, entry.intersectionRect);
            }
            setRatio(ratio);
            intersection.current = entry;
        },
        { threshold: 1 },
    );
    return (
        <Box sx={{ display: "flex" }}>
            {ratio >= 1 ? (
                <>
                    <Box ref={ref} sx={{ paddingX: 0.75, paddingY: 0.5, whiteSpace: "nowrap" }}>
                        {children}
                    </Box>
                    {showDivider ? <Divider orientation="vertical" flexItem /> : null}
                </>
            ) : (
                <Box
                    ref={ref}
                    style={{
                        width: maxSize?.width,
                        height: maxSize?.height,
                    }}
                    sx={{
                        paddingX: 0.75,
                        paddingY: 0.5,
                        whiteSpace: "nowrap",
                        display: "flex",
                        alignItems: "center",
                    }}
                >
                    {expandButton || <Box sx={{ background: "blue", flex: 1 }}></Box>}
                </Box>
            )}
        </Box>
    );
};
