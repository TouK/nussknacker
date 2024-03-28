import React, { createContext, PropsWithChildren, useContext, useEffect, useMemo, useRef, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { togglePanel } from "../../actions/nk";
import { isLeftPanelOpened, isRightPanelOpened } from "../../reducers/selectors/toolbars";
import { Graph } from "../graph/Graph";
import { useGraph } from "../graph/GraphContext";
import SidePanelToggleButton from "../SidePanelToggleButton";
import { StyledCollapsibleScrollPanel } from "./CollapsibleScrollPanel";

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

// adjust viewport for PanZoomPlugin.panToCells
function useGraphViewportAdjustment(side: keyof Graph["viewportAdjustment"], isOccupied: boolean) {
    const ref = useRef<HTMLDivElement>();
    const getGraph = useGraph();
    useEffect(() => {
        getGraph?.()?.adjustViewport({
            [side]: isOccupied ? ref.current?.getBoundingClientRect().width : 0,
        });
    }, [getGraph, isOccupied, side]);
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

export function SidePanel(props: SidePanelProps) {
    const { children, side } = props;
    const { isOpened, onToggle } = useSidePanelToggle(side);
    const [showToggle, setShowToggle] = useState(true);

    const ref = useGraphViewportAdjustment(side === PanelSide.Left ? "left" : "right", isOpened && showToggle);

    return (
        <SidePanelContext.Provider value={side}>
            {!isOpened || showToggle ? <SidePanelToggleButton type={side} isOpened={isOpened} onToggle={onToggle} /> : null}
            <StyledCollapsibleScrollPanel ref={ref} onScrollToggle={setShowToggle} isCollapsed={isOpened} side={side}>
                {children}
            </StyledCollapsibleScrollPanel>
        </SidePanelContext.Provider>
    );
}
