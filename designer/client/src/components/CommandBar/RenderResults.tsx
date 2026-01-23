import { type ActionImpl, KBarResults, useMatches } from "kbar";
import React from "react";

import { ResultItem } from "./ResultFlagItem";
import { SectionHeader } from "./SectionHeader";

function flattenAction(action: ActionImpl): ActionImpl {
    if (action.children.length !== 1) return action;
    return flattenAction(action.children[0]);
}

export function RenderResults() {
    const { results, rootActionId } = useMatches();
    return (
        <KBarResults
            items={results}
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
