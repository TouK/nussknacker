import { curryRight } from "lodash";
import { StickyNotesSettings } from "../../actions/nk";
import { stickyNoteComponentGroup } from "../../components/toolbars/creator/StickyNoteComponent";
import { ComponentGroup } from "../../types";

export const appendStickyNotes = curryRight((groups: ComponentGroup[], stickyNotesSettings: StickyNotesSettings, pristine: boolean) => {
    if (!stickyNotesSettings?.enabled) return groups;
    return groups.concat(stickyNoteComponentGroup(pristine));
});
