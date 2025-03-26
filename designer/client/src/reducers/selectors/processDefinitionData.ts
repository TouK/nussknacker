import type { Many } from "lodash";
import { compact, flow, isEqual } from "lodash";
import { createSelector, createSelectorCreator, defaultMemoize } from "reselect";

import type { ProcessDefinitionData } from "../../types";
import { ComponentGroup } from "../../types";
import { appendAdditionalCreators } from "./appendAdditionalCreators";
import { appendFragmentCreator } from "./appendFragmentCreator";
import { appendStickyNotes } from "./appendStickyNotes";
import { getComponentGroupsExtender } from "./componentGroups";
import { isFragment, isPristine } from "./graph";
import { getAdditionalComponents } from "./isCloudInstance";
import { getSettings, getStickyNotesSettings } from "./settings";
import { getUserSettings } from "./userSettings";

const createDeepEqualSelector = createSelectorCreator(defaultMemoize, isEqual);
const compactFlow = (...func: Array<Many<(...args: any[]) => any>>) => flow(...compact(func));

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
