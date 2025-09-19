import type { Many } from "lodash";
import { compact, flow } from "lodash";
import { createSelector } from "reselect";

import { StickyNoteType } from "../../components/graph/utils/stickyNotesUtils";
import type { ComponentGroup } from "../../types/component";
import { appendAdditionalCreators } from "./appendAdditionalCreators";
import { appendFragmentCreator } from "./appendFragmentCreator";
import { appendStickyNotes } from "./appendStickyNotes";
import { getScenarioGraph, isFragment } from "./graph";
import { getAdditionalComponents } from "./isCloudInstance";
import { getStickyNotesSettings } from "./settings";
import { getUserSettings } from "./userSettings";

const compactFlow = (...func: Array<Many<(...args: any[]) => any>>) => flow(...compact(func));

const getStickyNotesCount = createSelector(
    getScenarioGraph,
    (s): number | null => s?.nodes?.filter((n) => n.type === StickyNoteType).length,
);

export const getComponentGroupsExtender = createSelector(
    getStickyNotesSettings,
    getStickyNotesCount,
    isFragment,
    getUserSettings,
    getAdditionalComponents,
    (stickyNotesSettings, stickyNotesCount, isFragment, userSettings, additionalComponents): ((c: ComponentGroup[]) => ComponentGroup[]) =>
        compactFlow(
            userSettings["node.showFragmentCreator"] && appendFragmentCreator(isFragment),
            userSettings["cloud.showIntegrationsCreators"] && appendAdditionalCreators(additionalComponents),
            appendStickyNotes(stickyNotesSettings, stickyNotesCount, userSettings["node.advancedStickyNotes"]),
        ),
);
