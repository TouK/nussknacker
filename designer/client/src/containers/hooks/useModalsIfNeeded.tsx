import { useCallback } from "react";
import type { Scenario } from "src/components/Process/types";

import { ToolId } from "../../actions/nk/toolWindow";
import NodeUtils from "../../components/graph/NodeUtils";
import { useOpenProperties } from "../../components/toolbars/scenarioActions/buttons/PropertiesButton";
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

export function useModalsIfNeeded() {
    const { openNodeWindow } = useWindows();
    const openProperties = useOpenProperties();

    const getWindowsParams = useCallback(() => {
        const params = parseWindowsQueryParams({ nodeId: [], tool: [] });
        return [params.nodeId, params.tool];
    }, []);

    const openNodes = useCallback(
        (scenario: Scenario) => {
            const [nodeIds] = getWindowsParams();
            return nodeIds
                .map((id) => NodeUtils.getNodeByName(id, scenario.scenarioGraph) ?? NodeUtils.getNodeById(id, scenario.scenarioGraph))
                .filter(Boolean)
                .map((node) => openNodeWindow(node, scenario))
                .filter(Boolean);
        },
        [getWindowsParams, openNodeWindow],
    );

    const openToolWindows = useCallback(() => {
        const [, toolWindows] = getWindowsParams();
        return toolWindows
            .map((id) => {
                switch (id) {
                    case ToolId.properties:
                        return openProperties();
                }
            })
            .filter(Boolean);
    }, [getWindowsParams, openProperties]);

    const openFragmentNodes = useCallback(
        (fragment: Scenario) => {
            const prefix = getFragmentNodesPrefix(fragment);
            const [nodeIds] = getWindowsParams();
            return nodeIds
                .filter((i) => i.startsWith(prefix))
                .map((id) => NodeUtils.getNodeById(removePrefix(id, prefix), fragment.scenarioGraph))
                .filter(Boolean)
                .map((node) => openNodeWindow({ ...node, id: addPrefix(node.id, prefix) }, fragment, true))
                .filter(Boolean);
        },
        [getWindowsParams, openNodeWindow],
    );
    return {
        openNodes,
        openFragmentNodes,
        openToolWindows,
    };
}
