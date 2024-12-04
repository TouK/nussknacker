import { StickyNoteType } from "../../../types/stickyNote";
import { ComponentGroup } from "../../../types";

const noteModel = { id: "StickyNoteToAdd", type: StickyNoteType, isDisabled: false };
export const stickyNoteComponentGroup = (pristine: boolean) => {
    return [
        {
            components: [
                {
                    node: noteModel,
                    label: "sticky note",
                    componentId: StickyNoteType + "_" + pristine,
                    disabled: () => !pristine,
                },
            ],
            name: "stickyNotes",
        } as ComponentGroup,
    ];
};
