import { z } from "zod";

import { getActiveScenarios } from "../../../reducers/scenarios";
import { useAppDispatch } from "../../../store/storeHelpers";
import { useFrontendAiTool } from "../useFrontendAiTool";

export const ListScenariosAiTool = () => {
    const dispatch = useAppDispatch();

    useFrontendAiTool({
        toolName: "list_scenarios",
        description: `List all existing scenarios and fragments`,
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
