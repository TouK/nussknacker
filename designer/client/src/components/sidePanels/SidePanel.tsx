import React, { forwardRef, PropsWithChildren, useEffect, useRef, useState } from "react";
import ErrorBoundary from "../common/ErrorBoundary";
import { ScrollbarsExtended } from "./ScrollbarsExtended";
import TogglePanel from "../TogglePanel";
import { useDispatch, useSelector } from "react-redux";
import { isLeftPanelOpened, isRightPanelOpened } from "../../reducers/selectors/toolbars";
import { togglePanel } from "../../actions/nk/ui/layout";
import { useGraph } from "../graph/GraphContext";
import { Graph } from "../graph/Graph";
import styled from "@emotion/styled";

const scrollThumbSize = 8;
const sidebarWidth = 290;
const panelWidth = sidebarWidth + scrollThumbSize;

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
interface ScrollToggle {
    side: PanelSide;
    isOpened?: boolean;
}

type SidePanelProps = {
    side: PanelSide;
    className?: string;
};

export type Side = "LEFT" | "RIGHT";

export function useSidePanelToggle(side: Side) {
    const dispatch = useDispatch();
    const isOpened = useSelector(side === "RIGHT" ? isRightPanelOpened : isLeftPanelOpened);
    const onToggle = () => dispatch(togglePanel(side));
    return { isOpened, onToggle };
}

function checkLeftSide(props: ScrollToggle) {
    if (props.side === PanelSide.Left && !props.isOpened) {
        return -panelWidth;
    } else if (props.side === PanelSide.Left && props.isOpened) {
        return 0;
    }
}

function checkRightSide(props: ScrollToggle) {
    if (props.side === PanelSide.Right && !props.isOpened) {
        return -panelWidth;
    } else if (props.side === PanelSide.Right && props.isOpened) {
        return 0;
    }
}

const ScrollToggleChild = styled.div((props: ScrollToggle) => ({
    width: panelWidth,
    boxSizing: "border-box",
    minHeight: "100%",
    display: "flex",
    flexDirection: "column",
    pointerEvents: "none",
    alignItems: props.side === PanelSide.Left ? "flex-start" : "flex-end",
}));

const ScrollToggle = styled.div((props: ScrollToggle) => ({
    pointerEvents: "none",
    userSelect: "none",
    width: panelWidth,
    transition: "left 0.5s ease, right 0.5s ease",
    position: "absolute",
    zIndex: 1,
    top: 0,
    bottom: 0,
    overflow: "hidden",
    left: checkLeftSide(props),
    right: checkRightSide(props),
}));

const ScrollTogglePanel = forwardRef<HTMLDivElement, PropsWithChildren<Props>>(function ScrollTogglePanel(props, ref) {
    const { children, innerClassName, side, isCollapsed, onScrollToggle } = props;
    return (
        <ScrollToggle ref={ref} side={side} isOpened={isCollapsed}>
            <ScrollbarsExtended onScrollToggle={onScrollToggle} side={side}>
                <ErrorBoundary>
                    <ScrollToggleChild side={side} className={innerClassName}>
                        {children}
                    </ScrollToggleChild>
                </ErrorBoundary>
            </ScrollbarsExtended>
        </ScrollToggle>
    );
});

// adjust viewport for PanZoomPlugin.panToCells
function useGraphViewportAdjustment(side: keyof Graph["viewportAdjustment"], isOccupied: boolean) {
    const ref = useRef<HTMLDivElement>();
    const getGraph = useGraph();
    useEffect(() => {
        getGraph().adjustViewport({
            [side]: isOccupied ? ref.current.getBoundingClientRect().width : 0,
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
        <>
            {!isOpened || showToggle ? <TogglePanel type={side} isOpened={isOpened} onToggle={onToggle} /> : null}
            <ScrollTogglePanel ref={ref} onScrollToggle={setShowToggle} isCollapsed={isOpened} side={side} innerClassName={className}>
                {children}
            </ScrollTogglePanel>
        </>
    );
}
