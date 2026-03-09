import { Priority } from "kbar";
import React from "react";

import { getScenario } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import { delay } from "../../../utils";
import { useWindows } from "../../../windowManager/useWindows";
import { ComponentIcon } from "../../toolbars/creator/ComponentIcon";
import { useRegisterCommands } from "../useRegisterCommands";

export function useScenarioCommands() {
    const { openNodeWindow } = useWindows();
    const scenario = useAppSelector(getScenario);

    useRegisterCommands(
        () => [
            {
                id: `openNode/`,
                section: "scenario",
                name: "Nodes",
                priority: Priority.HIGH,
            },
            ...scenario.scenarioGraph.nodes.map((n, i) => {
                return {
                    id: `openNode/${n.id}`,
                    perform: async () => {
                        await delay(10);
                        openNodeWindow(n, scenario);
                    },
                    name: n.name,
                    icon: <ComponentIcon node={n} />,
                    parent: "openNode/",
                    priority: Priority.LOW,
                };
            }),
        ],
        [openNodeWindow, scenario],
    );
}
