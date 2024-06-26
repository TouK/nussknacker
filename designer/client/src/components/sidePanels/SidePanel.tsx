import React, {
    createContext,
    forwardRef,
    PropsWithChildren,
    useCallback,
    useContext,
    useEffect,
    useImperativeHandle,
    useMemo,
    useRef,
    useState,
} from "react";
import { useDispatch, useSelector } from "react-redux";
import { togglePanel } from "../../actions/nk";
import { isLeftPanelOpened, isRightPanelOpened } from "../../reducers/selectors/toolbars";
import { Graph } from "../graph/Graph";
import { useGraph } from "../graph/GraphContext";
import SidePanelToggleButton from "../SidePanelToggleButton";
import { StyledCollapsibleScrollPanel } from "./CollapsibleScrollPanel";
import { useMutationObserver } from "rooks";

export enum PanelSide {
    Right = "RIGHT",
    Left = "LEFT",
}

type SidePanelProps = PropsWithChildren<{
    side: PanelSide;
}>;

export function useSidePanelToggle(side: PanelSide) {
    const dispatch = useDispatch();
    const isOpened = useSelector(side === PanelSide.Right ? isRightPanelOpened : isLeftPanelOpened);
    const onToggle = () => dispatch(togglePanel(side));
    return { isOpened, onToggle };
}

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

    const calcOccupiedPart = useCallback((rect: DOMRect) => {
        if (!rect?.height) return 0;
        const availableHeight = rect.height;
        const usedHeight = [...ref.current.querySelectorAll(".draggable")]
            .map((e) => e.getBoundingClientRect())
            .reduce((value, current) => value + current.height, 0);
        return Math.min(1, Math.max(0, usedHeight / availableHeight));
    }, []);

    useEffect(() => {
        const occupiedPart = calcOccupiedPart(rect);
        getGraph?.()?.adjustViewport({
            [side]: inView ? rect?.width * occupiedPart : 0,
        });
    }, [calcOccupiedPart, getGraph, inView, rect, side]);

    return ref;
}

const SidePanelContext = createContext<PanelSide>(null);

export const useSidePanel = () => {
    const side = useContext(SidePanelContext);

    if (!side) {
        throw new Error(`${useSidePanel.name} was used outside of ${SidePanelContext.displayName} provider`);
    }

    const { isOpened, onToggle } = useSidePanelToggle(side);

    return useMemo(() => ({ side, isOpened, onToggle }), [isOpened, onToggle, side]);
};

export type SidePanelRef = {
    rect: DOMRect;
    side: PanelSide;
    isOpened: boolean;
};

export const SidePanel = forwardRef<SidePanelRef, SidePanelProps>(function SidePanel(props, forwardedRef) {
    const { children, side } = props;
    const { isOpened, onToggle } = useSidePanelToggle(side);
    const [showToggle, setShowToggle] = useState(true);

    const [ref, rect] = useRectObserver();
    useGraphViewportAdjustment({
        side: side === PanelSide.Left ? "left" : "right",
        inView: isOpened,
        rect,
        ref,
    });

    useImperativeHandle(
        forwardedRef,
        () => {
            return { side, isOpened, rect };
        },
        [isOpened, rect, side],
    );

    return (
        <SidePanelContext.Provider value={side}>
            {!isOpened || showToggle ? <SidePanelToggleButton type={side} isOpened={isOpened} onToggle={onToggle} /> : null}
            <StyledCollapsibleScrollPanel ref={ref} onScrollToggle={setShowToggle} isCollapsed={isOpened} side={side}>
                {children}
            </StyledCollapsibleScrollPanel>
        </SidePanelContext.Provider>
    );
});
