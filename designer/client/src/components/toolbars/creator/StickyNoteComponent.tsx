import { StickyNoteType } from "../../../types/stickyNote";
import { ComponentGroup } from "../../../types";

const noteModel = { id: "StickyNoteToAdd", type: StickyNoteType, isDisabled: false };
export const stickyNoteComponentGroup = (pristine: boolean) => {
    return [
        {
            components: [
                {
                    node: noteModel,
                    label: "Sticky Note",
                    componentId: StickyNoteType + "_" + pristine,
                    disabled: () => !pristine,
                },
            ],
            name: "Misc",
        } as ComponentGroup,
    ];
};
