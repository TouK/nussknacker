import { css } from "@emotion/css";
import { ExpandLess, MoreHoriz } from "@mui/icons-material";
import { Box, styled } from "@mui/material";
import type { CSSObject } from "@mui/styled-engine";
import { Allotment } from "allotment";
import type { AllotmentHandle } from "allotment/dist/types/src/allotment";
import { sum } from "lodash";
import type { PropsWithChildren } from "react";
import React, { useCallback, useRef, useState } from "react";
import "allotment/dist/style.css";
import { ErrorBoundary } from "react-error-boundary";

import { getScrollStyle } from "../node/StyledHeader";
import { VariableContextTree } from "./VariableContextTree";

const shadowClassName = css({
    boxShadow: "0px 0px 15px rgba(0,0,0,0.2)",
    zIndex: 10,
});

const Wrapper = styled(Box)({
    width: "100%",
    height: "100%",
    position: "relative",
});

export const InputOutputLayout = function InputOutputWrapper({ children }: PropsWithChildren) {
    const moveHandle = useRef<HTMLButtonElement>();
    const sizes = useRef<number[]>([]);
    const ref = useRef<AllotmentHandle>();

    const startPos = useRef(null);

    const onMouseDown = useCallback((e: React.MouseEvent) => {
        startPos.current = {
            x: e.clientX,
            y: e.clientY,
        };
        const [left, center, right] = sizes.current;

        const handleMouseMove = (moveEvent: MouseEvent) => {
            const deltaX = moveEvent.clientX - startPos.current.x;
            const sizes = [left + deltaX, center, right - deltaX];
            ref.current.resize(sizes);
        };

        const handleMouseUp = () => {
            startPos.current = null;
            document.removeEventListener("mousemove", handleMouseMove);
            document.removeEventListener("mouseup", handleMouseUp);
        };

        document.addEventListener("mousemove", handleMouseMove);
        document.addEventListener("mouseup", handleMouseUp);
    }, []);

    const prevSizes = useRef<number[]>([0, 0, 0]);

    const togglePanel = useCallback((index: number, collapsedSize: number, shouldCollapse = sizes.current[index] > collapsedSize * 2) => {
        const currentSize = sizes.current[index];
        const prevSize = prevSizes.current[index];
        const defaultSize = 0.2 * sum(sizes.current);
        const newSize = shouldCollapse ? collapsedSize : Math.max(prevSize, defaultSize);
        const updatedCenter = sizes.current[1] + (shouldCollapse ? currentSize : collapsedSize) - newSize;

        prevSizes.current = sizes.current;
        ref.current?.resize(
            sizes.current.map((size, i) => {
                if (i === index) return newSize;
                if (i === 1) return updatedCenter;
                return size;
            }),
        );
    }, []);

    const collapsedSize = 30;

    const [leftCollapsed, setLeftCollapsed] = useState(false);
    const [rightCollapsed, setRightCollapsed] = useState(false);
    const onChange = useCallback((currentSizes) => {
        const [left, center, right] = currentSizes;
        sizes.current = currentSizes;
        if (moveHandle.current) {
            moveHandle.current.style.left = `${left + center / 2}px`;
        }
        setLeftCollapsed(left < collapsedSize * 2);
        setRightCollapsed(right < collapsedSize * 2);
    }, []);

    const [leftHidden, setLeftHidden] = useState(false);
    const [rightHidden, setRightHidden] = useState(false);

    return (
        <Wrapper>
            <Allotment ref={ref} onChange={onChange} defaultSizes={[278, 820, 278]}>
                <Allotment.Pane
                    preferredSize="20%"
                    minSize={leftHidden ? 0 : collapsedSize}
                    maxSize={leftHidden ? 0 : Infinity}
                    visible={!leftHidden}
                >
                    <SidePanelBox
                        sx={{
                            alignItems: "flex-start",
                            overflowY: leftCollapsed ? "hidden" : "auto",
                        }}
                    >
                        <ErrorBoundary fallback={<div>{`ERROR`}</div>}>
                            <VariableContextTree
                                direction="input"
                                onIsEmptyChange={(isEmpty) => {
                                    setLeftHidden(isEmpty);
                                    if (isEmpty) {
                                        togglePanel(0, 0, true);
                                    }
                                }}
                            />
                        </ErrorBoundary>
                    </SidePanelBox>
                    <PanelButton side="left" collapsed={leftCollapsed} onClick={() => togglePanel(0, collapsedSize)}>
                        <ExpandLess />
                    </PanelButton>
                </Allotment.Pane>
                <Allotment.Pane preferredSize="60%" minSize={820} className={shadowClassName}>
                    {children}
                </Allotment.Pane>
                <Allotment.Pane
                    preferredSize="20%"
                    minSize={rightHidden ? 0 : collapsedSize}
                    maxSize={rightHidden ? 0 : Infinity}
                    visible={!rightHidden}
                >
                    <SidePanelBox
                        sx={{
                            alignItems: "flex-end",
                            overflowY: rightCollapsed ? "hidden" : "auto",
                        }}
                    >
                        <ErrorBoundary fallback={<div>{`ERROR`}</div>}>
                            <VariableContextTree
                                direction="output"
                                onIsEmptyChange={(isEmpty) => {
                                    setRightHidden(isEmpty);
                                    if (isEmpty) {
                                        togglePanel(2, 0, isEmpty);
                                    }
                                }}
                            />
                        </ErrorBoundary>
                    </SidePanelBox>
                    <PanelButton side="right" collapsed={rightCollapsed} onClick={() => togglePanel(2, collapsedSize)}>
                        <ExpandLess />
                    </PanelButton>
                </Allotment.Pane>
            </Allotment>
            <PanelButton side="center" ref={moveHandle} tabIndex={-1} onMouseDown={onMouseDown} onDoubleClick={() => ref.current.reset()}>
                <MoreHoriz />
            </PanelButton>
        </Wrapper>
    );
};

const SidePanelBox = styled(Box)(({ theme }) => ({
    height: "100%",
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    background: "rgba(0,0,0,.2)",
    overflowY: "auto",
    overflowX: "hidden",
    ...getScrollStyle(theme),
}));

const PanelButton = styled("button", {
    shouldForwardProp: (prop) => prop !== "side" && prop !== "collapsed",
})<{
    side?: "center" | "left" | "right";
    collapsed?: boolean;
}>(({ side, collapsed, theme }) => {
    const styles: CSSObject = {
        position: "absolute",
        bottom: "50%",
        zIndex: 20,
        padding: 0,
        margin: 0,
        border: 0,
        outline: 0,
        lineHeight: 0,
        background: "transparent",
        "&:focus": {
            color: theme.palette.action.active,
        },
    };
    switch (side) {
        case "left":
            return {
                ...styles,
                right: 0,
                transform: `translateY(-50%) translateX(35%) rotate(${collapsed ? 90 : -90}deg)`,
                paddingInline: 20,
            };
        case "right":
            return {
                ...styles,
                left: 0,
                transform: `translateY(-50%) translateX(-35%) rotate(${collapsed ? -90 : 90}deg)`,
                paddingInline: 20,
            };
        case "center":
            return {
                ...styles,
                left: "50%",
                bottom: "100%",
                transform: "translateY(50%) translateX(-50%)",
                paddingInline: 20,
                "&:focus": {
                    outline: 0,
                    color: "inherit",
                },
            };
        default:
            return styles;
    }
});
