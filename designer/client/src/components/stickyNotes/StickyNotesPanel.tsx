import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { ToolbarPanelProps } from "../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import Tool from "../toolbars/creator/Tool";
import { StyledToolbox } from "../toolbars/creator/ToolBox";
import { StickyNoteType } from "../../types/stickyNote";
import { useSelector } from "react-redux";
import { isPristine } from "../../reducers/selectors/graph";

export function StickyNotesPanel(props: ToolbarPanelProps): JSX.Element {
    const { t } = useTranslation();
    const pristine = useSelector(isPristine);
    const noteModel = { id: "StickyNoteToAdd", type: StickyNoteType, isDisabled: false };

    //One of stickyNotes simplifications is that we cannot add note to not saved scenario version
    const stickyNoteTool = useMemo(() => {
        return (
            <ToolbarWrapper {...props} title={t("panels.sticky-notes.title", "Sticky Notes Panel")}>
                <StyledToolbox id="toolbox">
                    <Tool
                        nodeModel={noteModel}
                        label={t("stickyNotes.tool.label", "sticky note")}
                        key={StickyNoteType}
                        disabled={!pristine}
                    />
                </StyledToolbox>
            </ToolbarWrapper>
        );
    }, [pristine]);
    return <>{stickyNoteTool}</>;
}
