import { useCallback, useRef } from "react";

export function useFirstRender() {
    const isFirstRenderRef = useRef(true);

    return useCallback(() => {
        const isFirstRender = isFirstRenderRef.current;
        isFirstRenderRef.current = false;

        return isFirstRender;
    }, []);
}
