import { useCallback } from "react";
import type { Scenario } from "src/components/Process/types";

import NodeUtils from "../../components/graph/NodeUtils";
import { useWindows } from "../../windowManager/useWindows";
import { parseWindowsQueryParams } from "./useSearchQuery";

export function getFragmentNodesPrefix(fragmentContent: Scenario) {
    return fragmentContent ? `${fragmentContent.name}-` : "";
}

function removePrefix(input: string, prefix: string): string {
    return input.startsWith(prefix) ? input.substring(prefix.length) : input;
}

function addPrefix(input: string, prefix: string): string {
    return input.startsWith(prefix) ? input : prefix + input;
}

export function useModalDetailsIfNeeded() {
    const { openNodeWindow } = useWindows();

    const getNodeIds = useCallback(() => {
        const params = parseWindowsQueryParams({ nodeId: [] });
        return params.nodeId;
    }, []);

    const openNodes = useCallback(
        (scenario: Scenario) => {
            return getNodeIds()
                .map((id) => NodeUtils.getNodeById(id, scenario.scenarioGraph))
                .filter(Boolean)
                .map((node) => openNodeWindow(node, scenario))
                .filter(Boolean);
        },
        [getNodeIds, openNodeWindow],
    );

    const openFragmentNodes = useCallback(
        (fragment: Scenario) => {
            const prefix = getFragmentNodesPrefix(fragment);
            return getNodeIds()
                .filter((i) => i.startsWith(prefix))
                .map((id) => NodeUtils.getNodeById(removePrefix(id, prefix), fragment.scenarioGraph))
                .filter(Boolean)
                .map((node) => openNodeWindow({ ...node, id: addPrefix(node.id, prefix) }, fragment, true))
                .filter(Boolean);
        },
        [getNodeIds, openNodeWindow],
    );
    return {
        openNodes,
        openFragmentNodes,
    };
}
