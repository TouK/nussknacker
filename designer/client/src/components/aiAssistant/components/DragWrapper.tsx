import { Box, styled } from "@mui/material";
import { g } from "jointjs";
import type { MouseEventHandler, PropsWithChildren } from "react";
import React, { useCallback, useMemo, useRef, useState } from "react";
import { useDocumentEventListener } from "rooks";

type DragWrapperProps = PropsWithChildren<{
    className?: string;
    onClick?: MouseEventHandler<HTMLElement>;
    snapRadius?: number;
}>;

const { setTimeout, clearTimeout } = window; // types fix

function DragWrapper({ children, className, onClick, snapRadius }: DragWrapperProps) {
    const [pos, setPos] = useState({ x: 0, y: 0 });
    const offsetRef = useRef({ x: 0, y: 0 });
    const dragTimeout = useRef(0);
    const dragging = useRef(false);
    const [isDragging, setIsDragging] = useState(false);
    const moved = useRef(false);

    const startingPos = useMemo(() => new g.Ellipse({ x: 0, y: 0 }, snapRadius || 100, snapRadius || 100), [snapRadius]);

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

    const pointerup = useCallback((event: PointerEvent) => {
        clearTimeout(dragTimeout.current);
        setIsDragging((dragging.current = false));
        document.body.style.userSelect = "";
    }, []);

    useDocumentEventListener("pointermove", pointermove);
    useDocumentEventListener("pointerup", pointerup);

    return (
        <Box
            className={className}
            style={{
                transform: startingPos.containsPoint(pos)
                    ? `translate(${startingPos.center().x}px, ${startingPos.center().y}px)`
                    : `translate(${pos.x}px, ${pos.y}px)`,
            }}
            sx={(theme) => ({
                transition: startingPos.containsPoint(pos)
                    ? theme.transitions.create("transform", { duration: theme.transitions.duration.shortest })
                    : null,
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
