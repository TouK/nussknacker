import { Box, styled } from "@mui/material";
import { g } from "jointjs";
import type { MouseEventHandler, PropsWithChildren } from "react";
import React, { useCallback, useMemo, useRef, useState } from "react";
import { useDocumentEventListener } from "rooks";

type DragWrapperProps = PropsWithChildren<{
    className?: string;
    onClick?: MouseEventHandler<HTMLElement>;
    snap?: number;
}>;

const { setTimeout, clearTimeout } = window; // types fix

function getTranslate({ x, y }: g.PlainPoint): string {
    return `translate(${x}px, ${y}px)`;
}

function getViewport(): g.Rect {
    return new g.Rect(0, 0, window.innerWidth, window.innerHeight);
}

function useSnapToBorder(snap: number) {
    const elementRef = useRef<HTMLElement>(null);
    const snapToBorder = useCallback(
        (pos: g.PlainPoint) => {
            if (!elementRef.current) return pos;

            const rect = new g.Rect(elementRef.current.getBoundingClientRect());
            const originalPos = rect.center().translate(-pos.x, -pos.y);
            const viewport = getViewport();
            const corners = [viewport.topLeft(), viewport.topRight(), viewport.bottomLeft(), viewport.bottomRight()];
            const dist = originalPos.chooseClosest(corners).difference(originalPos);
            const borders = viewport.clone().inflate(-Math.abs(dist.x), -Math.abs(dist.y));
            const containsRect = borders.clone().inflate(-snap).containsRect(rect);

            if (containsRect) return pos;

            const corner = rect.center().chooseClosest(corners);
            const adhereToRect = borders.pointNearestToPoint(corner.distance(rect.center()) <= snap * 2.5 ? corner : rect.center());
            return adhereToRect.difference(originalPos);
        },
        [snap],
    );

    return { elementRef, snapToBorder };
}

function DragWrapper({ children, className, onClick, snap = 50 }: DragWrapperProps) {
    const [pos, setPos] = useState({ x: 0, y: 0 });
    const offsetRef = useRef({ x: 0, y: 0 });
    const dragTimeout = useRef(0);
    const dragging = useRef(false);
    const [isDragging, setIsDragging] = useState(false);
    const moved = useRef(false);

    const startingPos = useMemo(() => new g.Ellipse({ x: 0, y: 0 }, snap, snap), [snap]);
    const { elementRef, snapToBorder } = useSnapToBorder(snap);

    const pointerdown = useCallback(
        (event: React.PointerEvent<HTMLElement>) => {
            if (event.button !== 0) return;
            clearTimeout(dragTimeout.current);
            moved.current = false;
            dragTimeout.current = setTimeout(() => {
                setIsDragging((dragging.current = true));
                offsetRef.current = {
                    x: event.clientX - pos.x,
                    y: event.clientY - pos.y,
                };
                document.body.style.userSelect = "none";
            }, 150);
        },
        [pos.x, pos.y],
    );

    const pointermove = useCallback((event: PointerEvent) => {
        if (!dragging.current) return;
        clearTimeout(dragTimeout.current);
        moved.current = true;
        setPos({
            x: event.clientX - offsetRef.current.x,
            y: event.clientY - offsetRef.current.y,
        });
    }, []);

    const pointerup = useCallback(
        (event: PointerEvent) => {
            clearTimeout(dragTimeout.current);
            setIsDragging((dragging.current = false));
            document.body.style.userSelect = "";
            setPos(snapToBorder);
        },
        [snapToBorder],
    );

    useDocumentEventListener("pointermove", pointermove);
    useDocumentEventListener("pointerup", pointerup);

    return (
        <Box
            className={className}
            ref={elementRef}
            style={{
                transform: getTranslate(pos),
            }}
            sx={(theme) => ({
                transition: isDragging
                    ? null
                    : theme.transitions.create("transform", {
                          duration: theme.transitions.duration.enteringScreen,
                          easing: "cubic-bezier(.4,0,0,1.25)",
                      }),
                pointerEvents: "none",
                "&>*": {
                    cursor: isDragging ? "grabbing" : null,
                    pointerEvents: "all",
                },
            })}
            onPointerDown={pointerdown}
            onClick={(event) => {
                if (moved.current) return;
                clearTimeout(dragTimeout.current);
                onClick?.(event);
            }}
            data-still={startingPos.containsPoint(pos) ? true : undefined}
        >
            {children}
        </Box>
    );
}

export default styled(DragWrapper)({});
