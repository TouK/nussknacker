import React, { forwardRef, PropsWithChildren, useEffect, useRef, useState } from "react";
import ErrorBoundary from "../common/ErrorBoundary";
import { ScrollbarsExtended } from "./ScrollbarsExtended";
import TogglePanel from "../TogglePanel";
import { useDispatch, useSelector } from "react-redux";
import { isLeftPanelOpened, isRightPanelOpened } from "../../reducers/selectors/toolbars";
import { togglePanel } from "../../actions/nk";
import { useGraph } from "../graph/GraphContext";
import { Graph } from "../graph/Graph";
import { StyledScrollToggle, StyledScrollToggleChild, StyledScrollTogglePanelWrapper } from "./StyledSidePanel";

export enum PanelSide {
    Right = "RIGHT",
    Left = "LEFT",
}

type Props = {
    isCollapsed?: boolean;
    className?: string;
    innerClassName?: string;
    side?: PanelSide;
    onScrollToggle?: (isEnabled: boolean) => void;
};

type SidePanelProps = {
    side: PanelSide;
    className?: string;
};

export type Side = "LEFT" | "RIGHT";

export function useSidePanelToggle(side: Side) {
    const dispatch = useDispatch();
    const isOpened = useSelector(side === PanelSide.Right ? isRightPanelOpened : isLeftPanelOpened);
    const onToggle = () => dispatch(togglePanel(side));
    return { isOpened, onToggle };
}

const ScrollTogglePanel = forwardRef<HTMLDivElement, PropsWithChildren<Props>>(function ScrollTogglePanel(props, ref) {
    const { children, innerClassName, side, isCollapsed, onScrollToggle } = props;
    return (
        <StyledScrollToggle ref={ref} side={side} isOpened={isCollapsed}>
            <ScrollbarsExtended onScrollToggle={onScrollToggle} side={side}>
                <ErrorBoundary>
                    <StyledScrollToggleChild side={side} className={innerClassName}>
                        {children}
                    </StyledScrollToggleChild>
                </ErrorBoundary>
            </ScrollbarsExtended>
        </StyledScrollToggle>
    );
});

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

export function SidePanel(props: PropsWithChildren<SidePanelProps>) {
    const { children, side, className } = props;
    const { isOpened, onToggle } = useSidePanelToggle(side);
    const [showToggle, setShowToggle] = useState(true);

    const ref = useGraphViewportAdjustment(side === PanelSide.Left ? "left" : "right", isOpened && showToggle);
    return (
        <StyledScrollTogglePanelWrapper>
            {!isOpened || showToggle ? <TogglePanel type={side} isOpened={isOpened} onToggle={onToggle} /> : null}
            <ScrollTogglePanel ref={ref} onScrollToggle={setShowToggle} isCollapsed={isOpened} side={side} innerClassName={className}>
                {children}
            </ScrollTogglePanel>
        </StyledScrollTogglePanelWrapper>
    );
}
