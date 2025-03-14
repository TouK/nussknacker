import { isEqual } from "lodash";
import { createSelectorCreator, defaultMemoize } from "reselect";
import { ProcessDefinitionData } from "../../types";
import { getSettings } from "./settings";

const createDeepEqualSelector = createSelectorCreator(defaultMemoize, isEqual);

export const getProcessDefinitionData = createDeepEqualSelector(
    getSettings,
    (s) => s.processDefinitionData || ({} as ProcessDefinitionData),
);
