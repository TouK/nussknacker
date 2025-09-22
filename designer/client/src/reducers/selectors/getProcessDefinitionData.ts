import { createSelector } from "reselect";

import type { ProcessDefinitionData } from "../../types/scenarioGraph";
import { getComponentGroupsExtender } from "./componentGroups";
import { getPlainProcessDefinitionData } from "./processDefinitionData";

export const getProcessDefinitionData = createSelector(
    getPlainProcessDefinitionData,
    getComponentGroupsExtender,
    ({ componentGroups = [], ...processDefinitionData }, extendComponentGroups): ProcessDefinitionData => ({
        ...processDefinitionData,
        componentGroups: extendComponentGroups(componentGroups),
    }),
);
