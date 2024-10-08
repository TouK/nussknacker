import React, { useEffect, useLayoutEffect, useMemo } from "react";
import { PanZoomPlugin } from "../../../components/graph/PanZoomPlugin";
import { createContextHook, useContextForward } from "../utils/context";
import { usePaper } from "./Paper";

type ContextType = PanZoomPlugin;
const Context = React.createContext<ContextType>(null);

export type PanZoomBehaviorProps = React.PropsWithChildren<{
    interactive?: boolean;
}>;

export const PanZoomBehavior = React.forwardRef<ContextType, PanZoomBehaviorProps>(function PanZoomBehavior(
    { children, interactive },
    forwardedRef,
) {
    const { paper } = usePaper();
    const behavior = useMemo<PanZoomPlugin>(() => paper && new PanZoomPlugin(paper), [paper]);
    useContextForward(forwardedRef, behavior);

    useLayoutEffect(() => {
        behavior?.fitContent();
        return () => {
            behavior?.remove();
        };
    }, [behavior]);

    useEffect(() => {
        behavior?.toggle(interactive);
    }, [interactive, behavior]);

    if (!behavior) return null;

    return <Context.Provider value={behavior}>{children}</Context.Provider>;
});

export const usePanZoomBehavior = createContextHook(Context, PanZoomBehavior);
