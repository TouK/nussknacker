import { type ActionImpl, KBarResults, useKBar, useMatches } from "kbar";
import React from "react";

import { ResultItem } from "./ResultFlagItem";
import { SectionHeader } from "./SectionHeader";

function flattenAction(action: ActionImpl): ActionImpl {
    if (action.children.length !== 1) return action;
    return flattenAction(action.children[0]);
}

export function RenderResults() {
    const { actions } = useKBar((state) => ({
        actions: Object.values(state.actions),
    }));
    const { results, rootActionId } = useMatches();
    const aiAssistant = actions?.find((r) => typeof r !== "string" && r.id === "ai-assistant");
    return (
        <KBarResults
            items={results.includes(aiAssistant) ? results : [...results, aiAssistant]}
            onRender={({ item, active }) =>
                typeof item === "string" ? (
                    <SectionHeader>{item}</SectionHeader>
                ) : (
                    <ResultItem action={flattenAction(item)} active={active} currentRootActionId={rootActionId} />
                )
            }
        />
    );
}
