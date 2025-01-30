import { createSelector } from "reselect";
import { stickyNoteComponentGroup } from "../../components/toolbars/creator/StickyNoteComponent";
import { isPristine } from "./graph";
import { getProcessDefinitionData, getStickyNotesSettings } from "./settings";

export const getComponentGroups = createSelector(
    getProcessDefinitionData,
    getStickyNotesSettings,
    isPristine,
    ({ componentGroups }, stickyNotesSettings, pristine) => {
        let groups = componentGroups;

        if (stickyNotesSettings.enabled) {
            groups = groups.concat(stickyNoteComponentGroup(pristine));
        }

        return groups;
    },
);
