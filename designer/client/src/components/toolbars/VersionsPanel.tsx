import React from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import { getCapabilities } from "../../reducers/selectors/other";
import ProcessHistory from "../history/ProcessHistory";
import { ToolbarPanelProps } from "../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../toolbarComponents/toolbarWrapper/ToolbarWrapper";

export function VersionsPanel(props: ToolbarPanelProps): JSX.Element {
    const { t } = useTranslation();
    const capabilities = useSelector(getCapabilities);

    return (
        <ToolbarWrapper {...props} title={t("panels.versions.title", "Versions")}>
            <ProcessHistory isReadOnly={!capabilities.editFrontend} />
        </ToolbarWrapper>
    );
}
