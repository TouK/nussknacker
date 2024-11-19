import { useWindows } from "../../windowManager";
import { useCallback } from "react";
import { Scenario } from "src/components/Process/types";
import { parseWindowsQueryParams } from "./useSearchQuery";
import NodeUtils from "../../components/graph/NodeUtils";

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
                .map((node) => openNodeWindow(node, scenario));
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
                .map((node) => openNodeWindow({ ...node, id: addPrefix(node.id, prefix) }, fragment, true));
        },
        [getNodeIds, openNodeWindow],
    );
    return {
        openNodes,
        openFragmentNodes,
    };
}
