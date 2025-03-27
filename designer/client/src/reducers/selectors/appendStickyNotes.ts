import { curryRight } from "lodash";

import type { StickyNotesSettings } from "../../actions/nk";
import { stickyNoteComponentGroup } from "../../components/toolbars/creator/StickyNoteComponent";
import type { ComponentGroup } from "../../types";

export const appendStickyNotes = curryRight(
    (groups: ComponentGroup[], stickyNotesSettings: StickyNotesSettings, stickyNotesCount: number) => {
        if (!stickyNotesSettings?.enabled) return groups;
        return groups.concat(stickyNoteComponentGroup(stickyNotesSettings, stickyNotesCount));
    },
);
