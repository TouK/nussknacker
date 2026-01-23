import { Box } from "@mui/material";
import type { ActionId, ActionImpl } from "kbar";
import type { PropsWithChildren } from "react";
import React, { forwardRef, useEffect, useMemo, useRef } from "react";
import { useMergeRefs } from "rooks";

import { ItemContent } from "./ItemContent";
import { ItemShortcuts } from "./ItemShortcuts";

export type ResultItemProps = PropsWithChildren<{
    action: ActionImpl;
    active: boolean;
    currentRootActionId: ActionId;
    preventClose?: boolean;
}>;

export const BaseResultItem = forwardRef<HTMLDivElement, ResultItemProps>(function ResultItem(
    { children, action, active, currentRootActionId, preventClose },
    forwardedRef,
) {
    const innerRef = useRef<HTMLDivElement>(null);
    const ref = useMergeRefs(innerRef, forwardedRef);

    useEffect(() => {
        if (!preventClose || !action.command?.perform) return;

        const parentElement = innerRef.current?.parentElement;
        if (!parentElement) return;

        const originalClick = parentElement.click.bind(innerRef.current);
        parentElement.click = () => action.command.perform(action);

        return () => {
            parentElement.click = originalClick;
        };
    }, [action, preventClose]);

    const ancestors = useMemo(() => {
        if (!currentRootActionId) return action.ancestors;
        const index = action.ancestors.findIndex((ancestor) => ancestor.id === currentRootActionId);
        // +1 removes the currentRootAction; e.g.
        // if we are on the "Set theme" parent action,
        // the UI should not display "Set theme… > Dark"
        // but rather just "Dark"
        return action.ancestors.slice(index + 1);
    }, [action.ancestors, currentRootActionId]);

    return (
        <Box
            ref={ref}
            sx={{
                padding: "12px 16px",
                background: active ? "var(--a1)" : "transparent",
                borderLeft: `2px solid ${active ? "var(--foreground)" : "transparent"}`,
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                cursor: "pointer",
            }}
        >
            <ItemContent action={action} ancestors={ancestors} />
            {children}
            <ItemShortcuts action={action} />
        </Box>
    );
});
