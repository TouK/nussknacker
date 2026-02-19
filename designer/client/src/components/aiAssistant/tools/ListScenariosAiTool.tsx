import { z } from "zod";

import { getActiveScenarios } from "../../../reducers/scenarios";
import { useAppDispatch } from "../../../store/storeHelpers";
import { useFrontendAiTool } from "../useFrontendAiTool";

export const ListScenariosAiTool = () => {
    const dispatch = useAppDispatch();

    useFrontendAiTool({
        toolName: "list_scenarios",
        description: `Returns a list of all available, non-archived scenarios and fragments with their names and properties. Use this to find scenario names before opening them.`,
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
