import { useCallback, useEffect, useState, useMemo } from "react";
import { usePreviousDifferent } from "rooks";

import type { VariableContextType } from "./VariableContextTree";

export function useNewlyAddedContexts(availableContexts: VariableContextType[], timeout = 3000) {
    const prevAvailableContexts = usePreviousDifferent(availableContexts);
    const [highlightedContexts, setHighlightedContexts] = useState<Record<string, number>>({});

    const findNewlyAddedContexts = useCallback(() => {
        if (!prevAvailableContexts) return [];
        return availableContexts.filter((context) => !prevAvailableContexts.some((prevContext) => prevContext.id === context.id));
    }, [availableContexts, prevAvailableContexts]);

    const addHighlightsForNewContexts = useCallback((newContexts: VariableContextType[]) => {
        if (newContexts.length === 0) return;

        setHighlightedContexts((prev) => {
            const currentTime = Date.now();
            const updates: Record<string, number> = {};

            newContexts.forEach((context) => {
                if (!prev[context.id]) {
                    updates[context.id] = currentTime;
                }
            });

            return Object.keys(updates).length > 0 ? { ...prev, ...updates } : prev;
        });
    }, []);

    const removeExpiredHighlights = useCallback(() => {
        const currentTime = Date.now();

        setHighlightedContexts((prev) => {
            const updatedHighlights = { ...prev };
            let changed = false;

            Object.entries(updatedHighlights).forEach(([id, timestamp]) => {
                if (currentTime - timestamp > timeout) {
                    delete updatedHighlights[id];
                    changed = true;
                }
            });

            return changed ? updatedHighlights : prev;
        });
    }, [timeout]);

    const isContextHighlighted = useCallback(
        (contextId: string) => Object.keys(highlightedContexts).includes(contextId),
        [highlightedContexts],
    );

    // Track and highlight new contexts
    useEffect(() => {
        const newContexts = findNewlyAddedContexts();
        addHighlightsForNewContexts(newContexts);
    }, [findNewlyAddedContexts, addHighlightsForNewContexts]);

    // Cleanup expired highlights
    useEffect(() => {
        const timer = setInterval(removeExpiredHighlights, 1000);
        return () => clearInterval(timer);
    }, [removeExpiredHighlights]);

    return isContextHighlighted;
}
