import type React from "react";
import type { Ref } from "react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useForkRef, useMutationObserver } from "rooks";

import type { Graph } from "../graph/Graph";
import { useGraph } from "../graph/GraphContext";

export function useRectObserver(): [ref: React.MutableRefObject<HTMLDivElement>, rect: DOMRect] {
    const ref = useRef<HTMLDivElement>(null);
    const [rect, setRect] = useState<DOMRect>(() => ref.current?.getBoundingClientRect());

    const options = useMemo(
        () => ({
            childList: true,
            attributes: true,
            subtree: true,
        }),
        [],
    );

    const callback = useCallback(() => {
        setRect(ref.current.getBoundingClientRect());
    }, []);

    useMutationObserver(ref, callback, options);

    return [ref, rect];
}

// adjust viewport for PanZoomPlugin.panToCells
export function useGraphViewportAdjustment({
    side,
    width,
    forwardedRef,
}: {
    width: number;
    side: keyof Graph["viewportAdjustment"] | null;
    forwardedRef?: Ref<HTMLDivElement>;
}): [ref: Ref<HTMLDivElement>] {
    const [rectRef, rect] = useRectObserver();
    const ref = useForkRef(rectRef, forwardedRef);

    const getGraph = useGraph();

    const calcOccupiedPart = useCallback(() => {
        if (!rect?.height) return 0;
        const availableHeight = rect.height;
        const usedHeight = [...rectRef.current.querySelectorAll(".draggable")]
            .map((e) => e.getBoundingClientRect())
            .reduce((value, current) => value + current.height, 0);
        return Math.min(1, Math.max(0, usedHeight / availableHeight));
    }, [rect?.height, rectRef]);

    useEffect(() => {
        if (!side) return;
        getGraph?.()?.adjustViewport({
            [side]: calcOccupiedPart() * width,
        });
    }, [calcOccupiedPart, getGraph, side, width]);

    return [ref];
}
