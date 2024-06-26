import React, { useCallback, useEffect, useRef, useState } from "react";
import { PanelSide } from "../../actions/nk";
import { Graph } from "../graph/Graph";
import { useGraph } from "../graph/GraphContext";
import { StyledCollapsibleScrollPanel } from "./CollapsibleScrollPanel";
import { useMutationObserver } from "rooks";
import { PropsOf } from "@emotion/react";
import { SideContextProvider, useSidePanel } from "./SidePanelsContext";

type SidePanelProps = PropsOf<typeof StyledCollapsibleScrollPanel>;

export function useRectObserver(): [ref: React.MutableRefObject<HTMLDivElement>, rect: DOMRect] {
    const ref = useRef<HTMLDivElement>();
    const [rect, setRect] = useState<DOMRect>(() => ref.current?.getBoundingClientRect());

    useMutationObserver(ref, () => setRect(ref.current.getBoundingClientRect()), {
        childList: true,
        attributes: true,
        subtree: true,
    });

    return [ref, rect];
}

// adjust viewport for PanZoomPlugin.panToCells
function useGraphViewportAdjustment({
    side,
    inView,
    rect,
    ref,
}: {
    side: keyof Graph["viewportAdjustment"];
    inView?: boolean;
    rect: DOMRect;
    ref: React.MutableRefObject<HTMLDivElement>;
}) {
    const getGraph = useGraph();

    const calcOccupiedPart = useCallback(
        (rect: DOMRect) => {
            if (!rect?.height) return 0;
            const availableHeight = rect.height;
            const usedHeight = [...ref.current.querySelectorAll(".draggable")]
                .map((e) => e.getBoundingClientRect())
                .reduce((value, current) => value + current.height, 0);
            return Math.min(1, Math.max(0, usedHeight / availableHeight));
        },
        [ref],
    );

    useEffect(() => {
        const occupiedPart = calcOccupiedPart(rect);
        getGraph?.()?.adjustViewport({
            [side]: inView ? rect?.width * occupiedPart : 0,
        });
    }, [calcOccupiedPart, getGraph, inView, rect, side]);

    return ref;
}

export const SidePanel = ({ side, ...props }: SidePanelProps) => {
    const { isOpened, toggleFullSize } = useSidePanel(side);

    const [ref, rect] = useRectObserver();
    useGraphViewportAdjustment({
        side: side === PanelSide.Left ? "left" : "right",
        inView: isOpened,
        rect,
        ref,
    });

    return (
        <SideContextProvider side={side}>
            <StyledCollapsibleScrollPanel ref={ref} onScrollToggle={toggleFullSize} isExpanded={isOpened} side={side} {...props} />
        </SideContextProvider>
    );
};
