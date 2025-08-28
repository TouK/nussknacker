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

function getTranslateTransform({ x, y }: g.PlainPoint): string {
    return `translate(${x}px, ${y}px)`;
}

function getViewport(): g.Rect {
    return new g.Rect(0, 0, window.innerWidth, window.innerHeight);
}

function useSnapToBorder(snap: number) {
    const elementRef = useRef<HTMLElement>(null);
    const snapToBorder = useCallback(
        (translation: g.PlainPoint) => {
            if (!elementRef.current) return translation;

            const viewport = getViewport();
            const corners = [viewport.topLeft(), viewport.topRight(), viewport.bottomLeft(), viewport.bottomRight()];

            const elRect = new g.Rect(elementRef.current.getBoundingClientRect());
            const rawElPos = elRect.center().translate(-translation.x, -translation.y);
            const initialDistance = rawElPos.chooseClosest(corners).difference(rawElPos);
            const borders = viewport.clone().inflate(-Math.abs(initialDistance.x), -Math.abs(initialDistance.y));

            const triggerRect = borders.clone().inflate(-snap);
            if (triggerRect.containsRect(elRect)) return translation;

            const closestCorner = elRect.center().chooseClosest(corners);
            const adhereToRect = borders.pointNearestToPoint(
                closestCorner.distance(elRect.center()) <= snap * 2.5 ? closestCorner : elRect.center(),
            );
            return adhereToRect.difference(rawElPos);
        },
        [snap],
    );

    return { elementRef, snapToBorder };
}

function DragWrapper({ children, className, onClick, snap = 50 }: DragWrapperProps) {
    const [translation, setTranslation] = useState({ x: 0, y: 0 });
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
                    x: event.clientX - translation.x,
                    y: event.clientY - translation.y,
                };
                document.body.style.userSelect = "none";
            }, 150);
        },
        [translation.x, translation.y],
    );

    const pointermove = useCallback((event: PointerEvent) => {
        if (!dragging.current) return;
        clearTimeout(dragTimeout.current);
        moved.current = true;
        setTranslation({
            x: event.clientX - offsetRef.current.x,
            y: event.clientY - offsetRef.current.y,
        });
    }, []);

    const pointerup = useCallback(() => {
        clearTimeout(dragTimeout.current);
        setIsDragging((dragging.current = false));
        document.body.style.userSelect = "";
        setTranslation(snapToBorder);
    }, [snapToBorder]);

    useDocumentEventListener("pointermove", pointermove);
    useDocumentEventListener("pointerup", pointerup);

    return (
        <Box
            className={className}
            ref={elementRef}
            style={{
                transform: getTranslateTransform(translation),
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
            data-originalPosition={startingPos.containsPoint(translation) ? true : undefined}
        >
            {children}
        </Box>
    );
}

export default styled(DragWrapper)({});
