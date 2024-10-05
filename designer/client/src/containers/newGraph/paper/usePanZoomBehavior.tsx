import { useEffect, useLayoutEffect, useMemo, useState } from "react";
import { PanZoomPlugin } from "../../../components/graph/PanZoomPlugin";
import { PaperContextType } from "./Paper";

export function usePanZoomBehavior(
    { paper }: PaperContextType,
    register: (behavior: PanZoomPlugin) => void,
    { interactive }: { interactive?: boolean },
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
