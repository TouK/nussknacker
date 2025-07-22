import { useCallback, useEffect, useState } from "react";
import { usePreviousDifferent } from "rooks";

import type { VariableContextType } from "./VariableContextTree";

export function useNewlyAddedContexts(availableContexts: VariableContextType[], timeout = 3000) {
    const prevAvailableContexts = usePreviousDifferent(availableContexts);
    const [highlightedContext, setHighlightedContext] = useState<{ id: string; timestamp: number } | null>(null);

    const findNewlyAddedContexts = useCallback(() => {
        if (!prevAvailableContexts) return [];
        return availableContexts.filter((context) => !prevAvailableContexts.some((prevContext) => prevContext.id === context.id));
    }, [availableContexts, prevAvailableContexts]);

    const getMostRecentContext = useCallback((contexts: VariableContextType[]) => {
        if (contexts.length === 0) return null;
        return contexts[contexts.length - 1];
    }, []);

    const highlightContext = useCallback((context: VariableContextType) => {
        setHighlightedContext({
            id: context.id,
            timestamp: Date.now(),
        });
    }, []);

    // Detect newly added contexts and highlight the most recent one
    useEffect(() => {
        const newContexts = findNewlyAddedContexts();
        const mostRecentContext = getMostRecentContext(newContexts);

        if (mostRecentContext) {
            highlightContext(mostRecentContext);
        }
    }, [findNewlyAddedContexts, getMostRecentContext, highlightContext]);

    // Clear highlight after timeout
    useEffect(() => {
        if (!highlightedContext) return;

        const timerId = setTimeout(() => {
            setHighlightedContext(null);
        }, timeout);

        return () => clearTimeout(timerId);
    }, [highlightedContext, timeout]);

    // Check if a specific context is currently highlighted
    const isContextHighlighted = useCallback((contextId: string) => highlightedContext?.id === contextId, [highlightedContext]);

    return isContextHighlighted;
}
