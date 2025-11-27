import { css } from "@emotion/css";
import { ExpandLess, MoreHoriz } from "@mui/icons-material";
import { Box, styled } from "@mui/material";
import { Allotment } from "allotment";
import type { AllotmentHandle } from "allotment/dist/types/src/allotment";
import { produce } from "immer";
import type { PropsWithChildren } from "react";
import React, { memo, useCallback, useRef, useState } from "react";

import "allotment/dist/style.css";
import { PanelButton } from "./PanelButton";
import { SidePane } from "./SidePane";

const shadowClassName = css({
    boxShadow: "0px 0px 15px rgba(0,0,0,0.2)",
    zIndex: 10,
});

const Wrapper = styled(Box)(({ theme }) => ({
    width: "100%",
    height: "100%",
    position: "relative",
    "--focus-border": theme.palette.primary.dark,
}));

export type Side = "right" | "left";

export type SideState<S extends Side = Side> = {
    side: S;
    collapsed: boolean;
    hidden: boolean;
};

export type SidesState<K extends Side = Side> = {
    [P in K]: SideState<P>;
};

export const InputOutputLayout = memo(function InputOutputWrapper({ children }: PropsWithChildren) {
    const moveHandle = useRef<HTMLButtonElement>(null);
    const defaultSize = 272;
    const defaultSizes = [0, 900, 0];
    const sizes = useRef<number[]>(defaultSizes);
    const ref = useRef<AllotmentHandle>(null);

    const startPos = useRef(null);

    const onMouseDown = useCallback((e: React.MouseEvent) => {
        startPos.current = {
            x: e.clientX,
            y: e.clientY,
        };
        const [left, center, right] = sizes.current;

        const handleMouseMove = (moveEvent: MouseEvent) => {
            const deltaX = moveEvent.clientX - startPos.current.x;
            const newSizes = [left + deltaX, center, right - deltaX];
            sizes.current = newSizes;
            ref.current.resize(newSizes);
        };

        const handleMouseUp = () => {
            startPos.current = null;
            document.removeEventListener("mousemove", handleMouseMove);
            document.removeEventListener("mouseup", handleMouseUp);
        };

        document.addEventListener("mousemove", handleMouseMove);
        document.addEventListener("mouseup", handleMouseUp);
    }, []);

    const prevSizes = useRef<number[]>(defaultSizes);

    const collapsedSize = 8;
    const collapseThreshold = 100;
    const togglePanel = useCallback(
        (index: number, collapsedSize: number, shouldCollapse = sizes.current[index] > collapseThreshold) => {
            const currentSize = sizes.current[index];
            const prevSize = prevSizes.current[index];

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
        },
        [collapseThreshold, defaultSize],
    );

    const [sidesState, setSidesState] = useState<SidesState>({
        left: { side: "left", collapsed: false, hidden: true },
        right: { side: "right", collapsed: false, hidden: true },
    });

    const onChange = useCallback(
        (currentSizes) => {
            const [leftSize, centerSize, rightSize] = currentSizes;
            sizes.current = currentSizes;
            if (moveHandle.current) {
                moveHandle.current.style.left = `${leftSize + centerSize / 2}px`;
            }

            setSidesState(
                produce(({ left, right }) => {
                    left.collapsed = leftSize < collapseThreshold;
                    right.collapsed = rightSize < collapseThreshold;
                }),
            );
        },
        [collapseThreshold],
    );

    const onChangeEnd = useCallback(() => {
        const [leftSize, centerSize, rightSize] = sizes.current;
        if (leftSize < collapseThreshold) {
            togglePanel(0, collapsedSize, true);
        }
        if (rightSize < collapseThreshold) {
            togglePanel(2, collapsedSize, true);
        }
    }, [togglePanel]);

    const onIsEmptyChange = useCallback(
        (side: "left" | "right", isEmpty: boolean) => {
            setSidesState(
                produce((draft) => {
                    draft[side].hidden = isEmpty;
                }),
            );
            // delay initial show
            setTimeout(() => {
                togglePanel(side === "left" ? 0 : 2, 0, isEmpty);
            }, 500);
        },
        [togglePanel],
    );

    const onToggleClick = useCallback((i: "left" | "right") => togglePanel(i === "left" ? 0 : 2, collapsedSize), [togglePanel]);

    const onResetClick = useCallback(() => ref.current.reset(), []);

    return (
        <Wrapper>
            <Allotment separator={false} ref={ref} onChange={onChange} defaultSizes={defaultSizes} onDragEnd={onChangeEnd}>
                <Allotment.Pane
                    preferredSize={sidesState.left.hidden ? 0 : defaultSize}
                    minSize={sidesState.left.hidden ? 0 : collapsedSize}
                    maxSize={sidesState.left.hidden ? 0 : Infinity}
                    visible={!sidesState.left.hidden}
                >
                    <SidePane sideState={sidesState.left} onIsEmptyChange={onIsEmptyChange} />
                </Allotment.Pane>
                <Allotment.Pane minSize={820} className={shadowClassName}>
                    {sidesState.left.hidden ? null : (
                        <PanelButton
                            side={sidesState.left.side}
                            collapsed={sidesState.left.collapsed}
                            onClick={(e) => {
                                onToggleClick(sidesState.left.side);
                            }}
                        >
                            <ExpandLess />
                        </PanelButton>
                    )}
                    {sidesState.right.hidden ? null : (
                        <PanelButton
                            side={sidesState.right.side}
                            collapsed={sidesState.right.collapsed}
                            onClick={(e) => {
                                onToggleClick(sidesState.right.side);
                            }}
                        >
                            <ExpandLess />
                        </PanelButton>
                    )}
                    {children}
                </Allotment.Pane>
                <Allotment.Pane
                    preferredSize={sidesState.right.hidden ? 0 : defaultSize}
                    minSize={sidesState.right.hidden ? 0 : collapsedSize}
                    maxSize={sidesState.right.hidden ? 0 : Infinity}
                    visible={!sidesState.right.hidden}
                >
                    <SidePane sideState={sidesState.right} onIsEmptyChange={onIsEmptyChange} />
                </Allotment.Pane>
            </Allotment>
            <PanelButton
                side="center"
                ref={moveHandle}
                tabIndex={-1}
                onMouseDown={onMouseDown}
                onMouseUp={() => {
                    setTimeout(() => onChangeEnd());
                }}
                onDoubleClick={onResetClick}
            >
                <MoreHoriz />
            </PanelButton>
        </Wrapper>
    );
});
