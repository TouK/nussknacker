import { useTheme } from "@mui/material";
import React, { createContext, forwardRef, PropsWithChildren, useContext, useEffect, useMemo, useRef, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { togglePanel } from "../../actions/nk";
import { isLeftPanelOpened, isRightPanelOpened } from "../../reducers/selectors/toolbars";
import ErrorBoundary from "../common/ErrorBoundary";
import { Graph } from "../graph/Graph";
import { useGraph } from "../graph/GraphContext";
import TogglePanel from "../TogglePanel";
import { ScrollbarsExtended } from "./ScrollbarsExtended";
import { StyledScrollToggle, StyledScrollToggleChild, styledScrollTogglePanelWrapper } from "./SidePanelStyled";

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
    const { children, innerClassName, side, isCollapsed, onScrollToggle, className } = props;
    return (
        <StyledScrollToggle ref={ref} side={side} isOpened={isCollapsed} className={className}>
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

const SidePanelContext = createContext<PanelSide>(null);

export const useSidePanel = () => {
    const side = useContext(SidePanelContext);

    if (!side) {
        throw new Error(`${useSidePanel.name} was used outside of ${SidePanelContext.displayName} provider`);
    }

    const { isOpened, onToggle } = useSidePanelToggle(side);

    return useMemo(() => ({ side, isOpened, onToggle }), [isOpened, onToggle, side]);
};

export function SidePanel(props: PropsWithChildren<SidePanelProps>) {
    const { children, side, className } = props;
    const { isOpened, onToggle } = useSidePanelToggle(side);
    const [showToggle, setShowToggle] = useState(true);
    const theme = useTheme();

    const ref = useGraphViewportAdjustment(side === PanelSide.Left ? "left" : "right", isOpened && showToggle);
    return (
        <SidePanelContext.Provider value={side}>
            {!isOpened || showToggle ? <TogglePanel type={side} isOpened={isOpened} onToggle={onToggle} /> : null}
            <ScrollTogglePanel
                className={styledScrollTogglePanelWrapper(theme)}
                ref={ref}
                onScrollToggle={setShowToggle}
                isCollapsed={isOpened}
                side={side}
                innerClassName={className}
            >
                {children}
            </ScrollTogglePanel>
        </SidePanelContext.Provider>
    );
}
