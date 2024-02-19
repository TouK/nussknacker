import { useCallback, useEffect, useState } from "react";

function useWindowVisibility() {
    const [active, setActive] = useState(() => document.visibilityState === "visible");

    const visibilityChange = useCallback(() => {
        setActive(() => document.visibilityState === "visible");
    }, []);

    useEffect(() => {
        document.addEventListener("visibilitychange", visibilityChange);
        return () => document.removeEventListener("visibilitychange", visibilityChange);
    }, [visibilityChange]);

    return active;
}

type IntervalOptions = {
    refreshTime: number;
    ignoreFirst?: boolean;
    disabled?: boolean;
};

export function useInterval(action: () => void, { refreshTime, ignoreFirst, disabled }: IntervalOptions): void {
    const isWindowActive = useWindowVisibility();
    const enabled = !disabled && isWindowActive;

    useEffect(() => {
        if (ignoreFirst || !enabled) return;

        action();
    }, [action, enabled, ignoreFirst]);

    useEffect(() => {
        if (!enabled) return;

        const interval = setInterval(() => action(), refreshTime);
        return () => clearInterval(interval);
    }, [action, enabled, refreshTime]);
}
