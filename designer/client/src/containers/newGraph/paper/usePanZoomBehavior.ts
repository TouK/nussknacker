import { useEffect, useLayoutEffect, useMemo, useState } from "react";
import { PanZoomPlugin } from "../../../components/graph/PanZoomPlugin";
import { PaperBehaviorProps, PaperContextType } from "./Paper";

export function usePanZoomBehavior(
    [{ paper }, register]: [PaperContextType, (behavior: PanZoomPlugin) => void],
    { interactive }: PaperBehaviorProps,
) {
    const [_register] = useState(() => register);
    const behavior = useMemo<PanZoomPlugin>(() => paper && new PanZoomPlugin(paper), [paper]);

    useLayoutEffect(() => {
        _register(behavior);
    }, [_register, behavior]);

    useLayoutEffect(() => {
        behavior?.fitContent();
        return () => {
            behavior?.remove();
        };
    }, [behavior]);

    useEffect(() => {
        behavior?.toggle(interactive);
    }, [interactive, behavior]);
}
