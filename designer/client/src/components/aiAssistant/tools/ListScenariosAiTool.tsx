import { z } from "zod";

import { getActiveScenarios } from "../../../reducers/scenarios";
import { useAppDispatch } from "../../../store/storeHelpers";
import { useFrontendAiTool } from "../useFrontendAiTool";

export const ListScenariosAiTool = () => {
    const dispatch = useAppDispatch();

    useFrontendAiTool({
        toolName: "list_scenarios",
        description: `Use this tool to get a list of all available, non-archived scenarios and fragments (processes). It returns a list of processes with their properties.`,
        parameters: z.object({}),
        execute: async () => {
            return dispatch((_, getState) => {
                const state = getState();
                return getActiveScenarios(state);
            });
        },
    });

    return null;
};
