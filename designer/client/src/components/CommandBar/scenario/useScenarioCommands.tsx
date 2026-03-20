import { Priority } from "kbar";
import React from "react";

import { getNodes, getScenario } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import type { NodeType } from "../../../types/node";
import { delay } from "../../../utils";
import { useWindows } from "../../../windowManager/useWindows";
import { useCallbackRef, useStableCallback } from "../../graph/node-modal/node/useCallbackRef";
import { ComponentIcon } from "../../toolbars/creator/ComponentIcon";
import { useRegisterCommands } from "../useRegisterCommands";

export function useScenarioCommands() {
    const { openNodeWindow } = useWindows();
    const scenario = useAppSelector(getScenario);
    const nodes = useAppSelector(getNodes);

    const open = useStableCallback((node: NodeType) => openNodeWindow(node, scenario));

    useRegisterCommands(() => {
        if (!nodes.length) return [];
        return [
            {
                id: `openNode/`,
                section: "scenario",
                name: "Nodes",
                priority: Priority.HIGH,
            },
            ...nodes.map((node) => ({
                id: `openNode/${node.id}`,
                perform: async () => {
                    await delay(10);
                    open(node);
                },
                name: node.name,
                icon: <ComponentIcon node={node} />,
                parent: "openNode/",
                priority: Priority.LOW,
            })),
        ];
    }, [nodes, open]);
}
