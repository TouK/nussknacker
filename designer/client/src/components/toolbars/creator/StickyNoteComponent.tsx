import type { StickyNotesSettings } from "../../../actions/nk";
import type { ComponentGroup } from "../../../types";
import { advancedNoteModel } from "../../graph/EspNode/stickyNote/advancedStickyNoteConfig";
import { basicNoteModel } from "../../graph/EspNode/stickyNote/basicStickyNoteConfig";
import { StickyNoteType } from "../../graph/utils/stickyNotesUtils";

export const stickyNoteComponentGroup = (
    stickyNotesSetting: StickyNotesSettings,
    stickyNotesCount: number,
    areAdvancedStickyNotesEnabled: boolean,
) => {
    const disabled = stickyNotesSetting.maxNotesCount && stickyNotesCount >= stickyNotesSetting.maxNotesCount;
    return [
        {
            components: [
                {
                    node: areAdvancedStickyNotesEnabled ? advancedNoteModel : basicNoteModel,
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
