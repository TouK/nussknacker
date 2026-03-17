import type { PropsOf } from "@emotion/react";
import { type ActionImpl, KBarResults, useKBar, useMatches } from "kbar";
import React, { useCallback, useMemo } from "react";

import { ResultItem } from "./ResultFlagItem";
import { SectionHeader } from "./SectionHeader";

function flattenAction(action: ActionImpl): ActionImpl {
    if (action.children.length !== 1) return action;
    return flattenAction(action.children[0]);
}

export function RenderResults() {
    const { aiAssistant, search } = useKBar((state) => {
        const actions = Object.values(state.actions);
        const aiAssistant = actions?.find((r) => typeof r !== "string" && r.id === "ai-assistant");
        return {
            actions,
            aiAssistant,
            search: state.searchQuery.trim(),
        };
    });
    const { results, rootActionId } = useMatches();

    const items = useMemo(() => {
        if (search.length && aiAssistant && !results.includes(aiAssistant)) {
            return [...results, aiAssistant];
        }
        return results;
    }, [aiAssistant, results, search.length]);

    const onRender: PropsOf<typeof KBarResults>["onRender"] = useCallback(
        ({ item, active }) =>
            typeof item === "string" ? (
                <SectionHeader>{item}</SectionHeader>
            ) : (
                <ResultItem action={flattenAction(item)} active={active} currentRootActionId={rootActionId} />
            ),
        [rootActionId],
    );

    return <KBarResults items={items} onRender={onRender} />;
}
