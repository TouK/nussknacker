import { compact, flow, Many } from "lodash";
import { createSelector } from "reselect";
import type { ComponentGroup } from "../../types";
import { appendAdditionalCreators } from "./appendAdditionalCreators";
import { appendFragmentCreator } from "./appendFragmentCreator";
import { appendStickyNotes } from "./appendStickyNotes";
import { isFragment, isPristine } from "./graph";
import { getAdditionalComponents } from "./isCloudInstance";
import { getStickyNotesSettings } from "./settings";
import { getUserSettings } from "./userSettings";

const compactFlow = (...func: Array<Many<(...args: any[]) => any>>) => flow(...compact(func));

export const getComponentGroupsExtender = createSelector(
    getStickyNotesSettings,
    isPristine,
    isFragment,
    getUserSettings,
    getAdditionalComponents,
    (stickyNotesSettings, pristine, isFragment, userSettings, additionalComponents): ((c: ComponentGroup[]) => ComponentGroup[]) =>
        compactFlow(
            userSettings["node.fragmentCreator"] && appendFragmentCreator(isFragment),
            userSettings["cloud.componentCreators"] && appendAdditionalCreators(additionalComponents),
            appendStickyNotes(stickyNotesSettings, pristine),
        ),
);
