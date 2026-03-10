import { useEffect } from "react";
import { z } from "zod";

import type { ThunkAction } from "../../../actions/reduxTypes";
import { fetchScenarios, getActiveScenarios } from "../../../reducers/scenarios";
import { useAppDispatch } from "../../../store/storeHelpers";
import { useFrontendAiTool } from "../useFrontendAiTool";

const listScenarios: ThunkAction = async (dispatch, getState) => {
    await dispatch(fetchScenarios());
    const state = getState();
    return getActiveScenarios(state);
};

export const ListScenariosAiTool = () => {
    const dispatch = useAppDispatch();

    useFrontendAiTool({
        toolName: "list_scenarios",
        description: `Returns a list of all available, non-archived scenarios and fragments with their names and properties. Use this to find scenario names before opening them.`,
        parameters: z.object({}),
        execute: () => dispatch(listScenarios),
    });

    return null;
};
