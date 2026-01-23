import type { Action } from "kbar";
import { useKBar } from "kbar";
import type { DependencyList } from "react";
import { useEffect, useMemo } from "react";

/**
 * eslint react-hooks/exhaustive-deps friendly version of kbar/useRegisterActions
 */
export function useRegisterCommands(actions: () => Action[], dependencies: DependencyList) {
    const { query } = useKBar();

    // eslint-disable-next-line react-hooks/exhaustive-deps
    const actionsCache = useMemo<Action[]>(() => actions?.(), dependencies);

    useEffect(() => {
        if (actionsCache.length) {
            const unregister = query.registerActions(actionsCache);
            return () => {
                unregister();
            };
        }
    }, [query, actionsCache]);
}
