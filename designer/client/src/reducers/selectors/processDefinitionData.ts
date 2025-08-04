import { isEqual } from "lodash";
import { createSelectorCreator, lruMemoize as defaultMemoize } from "reselect";

import type { ProcessDefinitionData } from "../../types";
import { getSettings } from "./settings";

const createDeepEqualSelector = createSelectorCreator(defaultMemoize, isEqual);

export const getPlainProcessDefinitionData = createDeepEqualSelector(
    getSettings,
    ({ processDefinitionData = {} }): ProcessDefinitionData => processDefinitionData,
);
