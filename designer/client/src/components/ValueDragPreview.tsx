import { Box, Paper, Typography } from "@mui/material";
import type { Dispatch, PropsWithChildren, SetStateAction } from "react";
import React, { createContext, forwardRef, useMemo, useState } from "react";
import { useDragDropManager, useDragLayer } from "react-dnd";
import { createPortal } from "react-dom";
import { ErrorBoundary } from "react-error-boundary";

import { useNotNull } from "./ComponentDragPreview";
import { DndTypes } from "./DndTypes";
import type { ExpressionLang } from "./graph/node-modal/editors/expression/types";
import { getPathString } from "./graph/node-modal/editors/expression/useAceDndTarget";
import type { SpelDndContext } from "./graph/node-modal/io/ContextTree";

export const AcceptedLangCtx = createContext<Dispatch<SetStateAction<ExpressionLang>>>(null);

function KeyPreview({ data }: { data: SpelDndContext }) {
    return <>{getPathString(data?.path)}</>;
}

function ValuePreview({ data }: { data: SpelDndContext }) {
    if (Array.isArray(data.value)) {
        const size = data.value.length;
        return (
            <>
                List ({size} {size === 1 ? "item" : "items"})
            </>
        );
    }
    if (data.value && typeof data.value === "object") {
        const size = Object.keys(data.value).length;
        return (
            <>
                Record ({size} {size === 1 ? "key" : "keys"})
            </>
        );
    }

    return <>{JSON.stringify(data.value)}</>;
}

function KeyValuePreview({ data }: { data: SpelDndContext }) {
    return (
        <Paper sx={{ padding: 1.5, paddingY: 1 }} elevation={5}>
            <Typography
                variant={data.type === "value" ? "caption" : "subtitle2"}
                sx={(theme) => ({
                    color: data.type === "key" ? theme.palette.info.main : "inherit",
                    opacity: data.type === "key" ? 1 : 0.5,
                })}
            >
                <KeyPreview data={data} />
            </Typography>
            {data.type === "value" ? (
                <Typography
                    variant="subtitle2"
                    sx={(theme) => ({
                        color: theme.palette.info.main,
                    })}
                >
                    <ValuePreview data={data} />
                </Typography>
            ) : null}
        </Paper>
    );
}

export const ValueDragPreview = forwardRef<HTMLDivElement, PropsWithChildren>(function ValueDragPreview({ children }, forwardedRef) {
    const manager = useDragDropManager();
    const monitor = manager.getMonitor();
    const targetIds = monitor.getTargetIds();

    const [_acceptedLang, setAcceptedLang] = useState<ExpressionLang>(null);
    const acceptedLang = useMemo(() => {
        const isOver = targetIds.some((id) => monitor.isOverTarget(id) && monitor.canDropOnTarget(id));
        if (isOver) {
            return _acceptedLang;
        }
        return null;
    }, [_acceptedLang, monitor, targetIds]);

    const { currentOffset, active, data } = useDragLayer((monitor) => ({
        data: monitor.getItemType() === DndTypes.VALUE && (monitor.getItem() as SpelDndContext),
        active: monitor.isDragging() && monitor.getItemType() === DndTypes.VALUE,
        currentOffset: monitor.getClientOffset(),
    }));

    const { x = 0, y = 0 } = useNotNull(currentOffset) || {};

    const portal = createPortal(
        <Box
            ref={forwardedRef}
            sx={{
                display: active && data && !acceptedLang ? "block" : "none",
                position: "fixed",
                top: 0,
                left: 0,
                zIndex: 1000000,
                pointerEvents: "none",
                userSelect: "none",
                transformOrigin: "top left",
                willChange: "transform",
            }}
            style={{ transform: `translate(${x}px, ${y}px)` }}
        >
            <ErrorBoundary fallback={null}>
                <KeyValuePreview data={data} />
            </ErrorBoundary>
        </Box>,
        document.body,
    );
    return (
        <>
            <AcceptedLangCtx.Provider value={setAcceptedLang}>{children}</AcceptedLangCtx.Provider>
            {portal}
        </>
    );
});
