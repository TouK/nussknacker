import type { StickyNotesSettings } from "../../../actions/nk";
import type { ComponentGroup, NodeType, StickyNoteNodeType } from "../../../types";
import { STICKY_NOTE_CONSTRAINTS, STICKY_NOTE_DEFAULT_COLOR } from "../../graph/EspNode/stickyNote";
import { StickyNoteType } from "../../graph/utils/stickyNotesUtils";

const dimensions = { width: STICKY_NOTE_CONSTRAINTS.DEFAULT_WIDTH, height: STICKY_NOTE_CONSTRAINTS.DEFAULT_HEIGHT };
const noteModel: StickyNoteNodeType = {
    id: "StickyNoteToAdd",
    type: StickyNoteType,
    isDisabled: false,
    content: "#### Double click to edit",
    dimensions: dimensions,
    color: STICKY_NOTE_DEFAULT_COLOR,
};

export const stickyNoteComponentGroup = (stickyNotesSetting: StickyNotesSettings, stickyNotesCount: number) => {
    const disabled = stickyNotesSetting.maxNotesCount && stickyNotesCount >= stickyNotesSetting.maxNotesCount;
    return [
        {
            components: [
                {
                    node: noteModel as NodeType,
                    label: "Sticky Note",
                    componentId: StickyNoteType + (disabled ? "_disabled" : ""),
                    disabled: () => disabled,
                    tooltip: disabled
                        ? `Max number of sticky notes [${stickyNotesSetting.maxNotesCount}] has been reached. You can change this in the app configuration.`
                        : null,
                },
            ],
            name: "Misc",
        } as ComponentGroup,
    ];
};
