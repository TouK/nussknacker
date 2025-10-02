import { Box } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { useCallback, useLayoutEffect, useRef } from "react";

import { useArrayStateWithUpdate } from "./useArrayStateWithUpdate";
import { WithSeparator } from "./withSeparator";

export function PrimaryElementsRow({
    children,
    onSpacingChange,
    onHiddenElementsChanged,
}: PropsWithChildren<{
    onSpacingChange?: (box?: DOMRectReadOnly | null) => void;
    onHiddenElementsChanged?: (hidden: Array<Exclude<React.ReactNode, boolean | null | undefined>>) => void;
}>) {
    const [metaArray, metaArrayControls] = useArrayStateWithUpdate<{ ratio: number; box: DOMRectReadOnly }>([]);
    const lastSpacerIndex = useRef<number>();

    const updateElement = useCallback(
        (i: number, ratio: number, box: DOMRectReadOnly) => (current) => {
            if (current?.ratio === ratio && current?.box === box) return current;
            if (0 < ratio && ratio < 1) {
                lastSpacerIndex.current = i;
                onSpacingChange?.(box);
            } else if (lastSpacerIndex.current === i) {
                onSpacingChange?.();
            }
            return { ...current, ratio, box };
        },
        [onSpacingChange],
    );

    useLayoutEffect(() => {
        onHiddenElementsChanged?.(React.Children.map(children, (child, index) => (metaArray[index]?.ratio < 1 ? child : null)));
    }, [children, metaArray, onHiddenElementsChanged]);

    return (
        <Box
            sx={{
                display: "flex",
                justifyContent: "flex-end",
                alignItems: "center",
                overflow: "hidden",
            }}
        >
            {React.Children.map(children, (child, i) => (
                <WithSeparator
                    key={i}
                    onVisibilityChange={(ratio, size) => {
                        metaArrayControls.updateItemAtIndex(i, updateElement(i, ratio, size));
                    }}
                    showDivider={0 <= i && i < React.Children.count(children) - 1}
                >
                    {child}
                </WithSeparator>
            ))}
        </Box>
    );
}
