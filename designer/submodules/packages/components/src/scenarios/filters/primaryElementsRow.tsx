import type { PropsWithChildren } from "react";
import React, { useCallback, useLayoutEffect, useRef } from "react";

import { ElementsRow } from "./elementsRow";
import { ElementWithSeparator } from "./elementWithSeparator";
import { useArrayStateWithUpdate } from "./useArrayStateWithUpdate";
import { VisibilitySensor } from "./visibilitySensor";

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

    const childrenArray = React.Children.toArray(children);
    if (childrenArray.length === 0) {
        return null;
    }
    return (
        <ElementsRow
            sx={{
                overflow: "hidden",
                // compensate some rounding problems with different zoom levels
                // some almost invisible value above 0.05% works well
                paddingInline: "0.075%",
            }}
        >
            {childrenArray.map((child, i, { length }) => (
                <VisibilitySensor
                    key={i}
                    onChange={(ratio, size) => {
                        metaArrayControls.updateItemAtIndex(i, updateElement(i, ratio, size));
                    }}
                >
                    <ElementWithSeparator showDivider={i < length - 1}>{child}</ElementWithSeparator>
                </VisibilitySensor>
            ))}
        </ElementsRow>
    );
}
