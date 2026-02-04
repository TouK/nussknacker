import { ThreadPrimitive, useAssistantEvent } from "@assistant-ui/react";
import { styled } from "@mui/material";
import { useContentScroll } from "@touk/window-manager";
import type { PropsWithChildren } from "react";
import React, { useCallback, useRef, useState } from "react";
import { useEventListenerRef, useMergeRefs, useResizeObserverRef } from "rooks";

import { ScrollToBottomButton } from "./ScrollToBottomButton";

const StyledThreadRoot = styled(ThreadPrimitive.Root)(({ theme }) => ({
    display: "flex",
    flexDirection: "column",
    margin: theme.spacing(2),
}));
const StyledThreadViewport = styled(ThreadPrimitive.Viewport)({
    paddingBottom: "10%",
});

export function ThreadWithScroll({ children }: PropsWithChildren) {
    const elRef = useRef<HTMLElement>(null);
    const lastHeight = useRef<number>(0);

    const [manualScrolling, setManualScrolling] = useState(false);
    useAssistantEvent("thread.run-start", () => setManualScrolling(false));

    const onResize = useCallback(() => {
        if (manualScrolling) return;
        if (!elRef.current) return;
        const diff = elRef.current.clientHeight - lastHeight.current;
        if (diff <= 20) return;
        elRef.current.scrollIntoView({ block: "end", behavior: diff < 300 ? "smooth" : "auto" });
        lastHeight.current = elRef.current.clientHeight;
    }, [manualScrolling]);
    const [resizeRef] = useResizeObserverRef(onResize);

    const { hasBottomOverflow } = useContentScroll();
    const onScroll = useCallback(() => setManualScrolling(hasBottomOverflow), [hasBottomOverflow]);
    const wheelListenerRef = useEventListenerRef("wheel", onScroll);
    const touchstartListenerRef = useEventListenerRef("touchstart", onScroll);
    const keydownListenerRef = useEventListenerRef("keydown", onScroll);

    const mergeRefs = useMergeRefs(elRef, resizeRef, wheelListenerRef, touchstartListenerRef, keydownListenerRef);

    return (
        <StyledThreadRoot>
            <StyledThreadViewport ref={mergeRefs}>{children}</StyledThreadViewport>
            <ScrollToBottomButton />
        </StyledThreadRoot>
    );
}
