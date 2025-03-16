import { isEqual } from "lodash";
import { createSelector, createSelectorCreator, defaultMemoize } from "reselect";
import { ProcessDefinitionData } from "../../types";
import { getComponentGroupsExtender } from "./componentGroups";
import { getSettings } from "./settings";

const createDeepEqualSelector = createSelectorCreator(defaultMemoize, isEqual);

const getPlainProcessDefinitionData = createDeepEqualSelector(
    getSettings,
    ({ processDefinitionData = {} }): ProcessDefinitionData => processDefinitionData,
);

export const getProcessDefinitionData = createSelector(
    getPlainProcessDefinitionData,
    getComponentGroupsExtender,
    ({ componentGroups = [], ...processDefinitionData }, extendComponentGroups): ProcessDefinitionData => ({
        ...processDefinitionData,
        componentGroups: extendComponentGroups(componentGroups),
    }),
);
