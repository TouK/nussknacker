import { css } from "@emotion/css";
import React, { forwardRef, ReactPortal, useEffect, useMemo, useState } from "react";
import { useDragDropManager, useDragLayer } from "react-dnd";
import { createPortal } from "react-dom";
import { useDebouncedValue } from "rooks";
import { NodeType } from "../types";
import { ComponentPreview } from "./ComponentPreview";
import { DndTypes } from "./toolbars/creator/Tool";
import { StickyNotePreview } from "./StickyNotePreview";
import { StickyNoteType } from "../types/stickyNote";

function useNotNull<T>(value: T) {
    const [current, setCurrent] = useState(() => value);
    useEffect(() => {
        if (!value) return;
        setCurrent(value);
    }, [value]);
    return current;
}

export const ComponentDragPreview = forwardRef<HTMLDivElement, { scale: () => number }>(function ComponentDragPreview(
    { scale },
    forwardedRef,
) {
    const manager = useDragDropManager();
    const monitor = manager.getMonitor();
    const { currentOffset, active, data } = useDragLayer((monitor) => ({
        data: monitor.getItem(),
        active: monitor.isDragging() && monitor.getItemType() === DndTypes.ELEMENT,
        currentOffset: monitor.getClientOffset(),
    }));

    const { x = 0, y = 0 } = useNotNull(currentOffset) || {};
    const node = useNotNull<NodeType>(data);

    const targetIds = monitor.getTargetIds();
    const isOver = useMemo(() => {
        return targetIds.some((id) => monitor.isOverTarget(id) && monitor.canDropOnTarget(id)) || monitor.didDrop();
    }, [monitor, targetIds]);

    const [activeDelayed] = useDebouncedValue(active, 500);
    const wrapperStyles = css({
        display: active || activeDelayed ? "block" : "none",
        position: "fixed",
        top: 0,
        left: 0,
        zIndex: 100,
        pointerEvents: "none",
        userSelect: "none",
        transformOrigin: "top left",
        willChange: "transform",
    });

    if (!node) {
        return null;
    }

    function createPortalForPreview(child: JSX.Element): ReactPortal {
        return createPortal(
            <div ref={forwardedRef} className={wrapperStyles} style={{ transform: `translate(${x}px, ${y}px)` }}>
                <div
                    style={{
                        transformOrigin: "top left",
                        transform: `scale(${scale()})`,
                    }}
                >
                    {child}
                </div>
            </div>,
            document.body,
        );
    }

    if (node?.type === StickyNoteType) return createPortalForPreview(<StickyNotePreview isActive={active} isOver={isOver} />);
    return createPortalForPreview(<ComponentPreview node={node} isActive={active} isOver={isOver} />);
});
