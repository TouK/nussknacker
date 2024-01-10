import { useWindows } from "../../windowManager";
import { useCallback } from "react";
import { Process } from "src/components/Process/types";
import { parseWindowsQueryParams } from "./useSearchQuery";
import NodeUtils from "../../components/graph/NodeUtils";

export function getFragmentNodesPrefix(fragmentContent: Process) {
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
        (process: Process) => {
            return getNodeIds()
                .map(
                    (id) => NodeUtils.getNodeById(id, process.json) ?? (process.name === id && NodeUtils.getProcessPropertiesNode(process)),
                )
                .filter(Boolean)
                .map((node) => openNodeWindow(node, process.json));
        },
        [getNodeIds, openNodeWindow],
    );

    const openFragmentNodes = useCallback(
        (fragment: Process) => {
            const prefix = getFragmentNodesPrefix(fragment);
            return getNodeIds()
                .filter((i) => i.startsWith(prefix))
                .map((id) => NodeUtils.getNodeById(removePrefix(id, prefix), fragment.json))
                .filter(Boolean)
                .map((node) => openNodeWindow({ ...node, id: addPrefix(node.id, prefix) }, fragment.json, true));
        },
        [getNodeIds, openNodeWindow],
    );
    return {
        openNodes,
        openFragmentNodes,
    };
}
