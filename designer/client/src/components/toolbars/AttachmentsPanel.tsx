import React from "react";
import { useTranslation } from "react-i18next";
import ProcessAttachments from "../processAttach/ProcessAttachments";
import { ToolbarPanelProps } from "../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../toolbarComponents/toolbarWrapper/ToolbarWrapper";

export function AttachmentsPanel(props: ToolbarPanelProps) {
    const { t } = useTranslation();

    return (
        <ToolbarWrapper {...props} title={t("panels.attachments.title", "Attachments")}>
            <ProcessAttachments />
        </ToolbarWrapper>
    );
}
