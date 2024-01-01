import { useEffect, useRef } from "react";

export function useInitEffect(callback: () => any) {
    const running = useRef(false);
    useEffect(() => {
        // avoid running twice for same callback (react 18)
        if (!running.current) {
            running.current = true;
            Promise.resolve()
                .then(callback)
                .finally(() => {
                    running.current = false;
                });
        }
    }, [callback]);
}
