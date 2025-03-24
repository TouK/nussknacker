import { StickyNoteType } from "../../../types/stickyNote";
import { ComponentGroup, NodeType, StickyNoteNodeType } from "../../../types";
import { STICKY_NOTE_CONSTRAINTS, STICKY_NOTE_DEFAULT_COLOR } from "../../graph/EspNode/stickyNote";
import { StickyNotesSettings } from "../../../actions/nk";

const dimensions = { width: STICKY_NOTE_CONSTRAINTS.DEFAULT_WIDTH, height: STICKY_NOTE_CONSTRAINTS.DEFAULT_HEIGHT };
const noteModel: StickyNoteNodeType = {
    id: "StickyNoteToAdd",
    type: StickyNoteType,
    isDisabled: false,
    content: "",
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
                },
            ],
            name: "Misc",
        } as ComponentGroup,
    ];
};
