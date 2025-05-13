import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";

import Icon from "../../../../assets/img/toolbarButtons/archive.svg";
import DialogMessages from "../../../../common/DialogMessages";
import { getProcessName, isArchivePossible } from "../../../../reducers/selectors/graph";
import { useWindows } from "../../../../windowManager";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import type { ToolbarButtonProps } from "../../types";
import { useArchiveHelper } from "./useArchiveHelper";

function ArchiveButton({ disabled, type }: ToolbarButtonProps): JSX.Element {
    const processName = useSelector(getProcessName);
    const { confirmArchiveCallback } = useArchiveHelper(processName);
    const archivePossible = useSelector(isArchivePossible);
    const available = !disabled && archivePossible;
    const { t } = useTranslation();
    const { confirm } = useWindows();

    const onClick = useCallback(
        () =>
            available &&
            confirm({
                text: DialogMessages.archiveProcess(processName),
                onConfirmCallback: confirmArchiveCallback,
                confirmText: t("panels.actions.process-archive.yes", "Yes"),
                denyText: t("panels.actions.process-archive.no", "No"),
            }),
        [available, confirm, processName, t, confirmArchiveCallback],
    );

    return (
        <CapabilitiesToolbarButton
            change
            name={t("panels.actions.process-archive.button", "archive")}
            icon={<Icon />}
            disabled={!available}
            onClick={onClick}
            type={type}
        />
    );
}

export default ArchiveButton;
