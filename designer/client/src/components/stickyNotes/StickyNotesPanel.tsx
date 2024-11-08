import React, { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";
import { ToolbarPanelProps } from "../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import Tool from "../toolbars/creator/Tool";
import { StyledToolbox } from "../toolbars/creator/ToolBox";
import { StickyNoteType } from "../../types/stickyNote";

export function StickyNotesPanel(props: ToolbarPanelProps): JSX.Element {
    const { t } = useTranslation();

    const noteModel = { id: "StickyNote", type: StickyNoteType, isDisabled: false };

    return (
        <ToolbarWrapper {...props} title={t("panels.sticky-notes.title", "Sticky Notes Panel")}>
            <StyledToolbox id="toolbox">
                <Tool nodeModel={noteModel} label="sticky note" />
            </StyledToolbox>
        </ToolbarWrapper>
    );
}
