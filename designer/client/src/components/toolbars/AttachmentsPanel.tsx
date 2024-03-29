import { ToolbarWrapper } from "../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import ProcessAttachments from "../processAttach/ProcessAttachments";
import React from "react";
import { useTranslation } from "react-i18next";

export function AttachmentsPanel() {
    const { t } = useTranslation();

    return (
        <ToolbarWrapper id="attachments-panel" title={t("panels.attachments.title", "Attachments")}>
            <ProcessAttachments />
        </ToolbarWrapper>
    );
}
