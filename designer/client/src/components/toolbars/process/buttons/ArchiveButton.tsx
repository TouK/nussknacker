import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import Icon from "../../../../assets/img/toolbarButtons/archive.svg";
import * as DialogMessages from "../../../../common/DialogMessages";
import { getProcessId, isArchivePossible } from "../../../../reducers/selectors/graph";
import { useWindows } from "../../../../windowManager";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import { ToolbarButtonProps } from "../../types";
import { useArchiveHelper } from "./useArchiveHelper";

function ArchiveButton({ disabled }: ToolbarButtonProps): JSX.Element {
    const processId = useSelector(getProcessId);
    const { confirmArchiveCallback } = useArchiveHelper(processId);
    const archivePossible = useSelector(isArchivePossible);
    const available = !disabled && archivePossible;
    const { t } = useTranslation();
    const { confirm } = useWindows();

    const onClick = useCallback(
        () =>
            available &&
            confirm({
                text: DialogMessages.archiveProcess(processId),
                onConfirmCallback: confirmArchiveCallback,
                confirmText: t("panels.actions.process-archive.yes", "Yes"),
                denyText: t("panels.actions.process-archive.no", "No"),
            }),
        [available, confirm, processId, t, confirmArchiveCallback],
    );

    return (
        <CapabilitiesToolbarButton
            change
            name={t("panels.actions.process-archive.button", "archive")}
            icon={<Icon />}
            disabled={!available}
            onClick={onClick}
        />
    );
}

export default ArchiveButton;
