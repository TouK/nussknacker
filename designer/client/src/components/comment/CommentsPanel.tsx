import React from "react";
import { useTranslation } from "react-i18next";
import { ToolbarPanelProps } from "../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import ProcessComments from "./ProcessComments";

export function CommentsPanel(props: ToolbarPanelProps) {
    const { t } = useTranslation();

    return (
        <ToolbarWrapper {...props} title={t("panels.comments.title", "Comments")}>
            <ProcessComments />
        </ToolbarWrapper>
    );
}
