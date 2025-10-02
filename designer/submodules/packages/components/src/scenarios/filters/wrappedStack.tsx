import { Box } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { useCallback, useRef } from "react";

import { useArrayStateWithUpdate } from "./useArrayStateWithUpdate";
import { WithSeparator } from "./withSeparator";

type WrappedStackProps = PropsWithChildren<{ onSpacingChange?: (box: DOMRectReadOnly | null) => void }>;

export const useWrappedStack = ({ children, onSpacingChange }: WrappedStackProps) => {
    const nodes = React.Children.toArray(children);

    const lastSpacerIndex = useRef<number>();
    const [elements, elementsControls] = useArrayStateWithUpdate<{ ratio: number; box: DOMRectReadOnly }>([]);
    const getOnInViewChange = useCallback(
        (i: number) =>
            function onInViewChange(ratio: number, box: DOMRectReadOnly) {
                elementsControls.updateItemAtIndex(i, (current) => {
                    if (current?.ratio === ratio && current?.box === box) return current;
                    if (0 < ratio && ratio < 1) {
                        lastSpacerIndex.current = i;
                        onSpacingChange?.(box);
                    } else if (lastSpacerIndex.current === i) {
                        onSpacingChange?.(null);
                    }
                    return { ...current, ratio, box };
                });
            },
        [elementsControls, onSpacingChange],
    );

    const primary = (
        <Box
            sx={{
                display: "flex",
                justifyContent: "flex-end",
                alignItems: "center",
                overflow: "hidden",
            }}
        >
            {nodes.map((child, i, array) => (
                <WithSeparator key={i} onVisibilityChange={getOnInViewChange(i)} showDivider={0 <= i && i < array.length - 1}>
                    {child}
                </WithSeparator>
            ))}
        </Box>
    );

    const secondaryNodes = nodes.filter((c, i) => elements[i]?.ratio < 1);
    const secondary =
        secondaryNodes.length > 0 ? (
            <Box sx={{ display: "flex", flexWrap: "wrap", justifyContent: "flex-end" }}>
                {secondaryNodes.map((child, i, array) => (
                    <WithSeparator key={i} showDivider={0 <= i && i < array.length - 1}>
                        {child}
                    </WithSeparator>
                ))}
            </Box>
        ) : null;

    return { primary, secondary };
};
