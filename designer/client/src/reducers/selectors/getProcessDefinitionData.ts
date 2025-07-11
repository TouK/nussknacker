import { createSelector } from "reselect";

import type { ProcessDefinitionData } from "../../types";
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
