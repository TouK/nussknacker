import React from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";

import Icon from "../../../../assets/img/toolbarButtons/save.svg";
import HttpService from "../../../../http/HttpService";
import {
    getProcessName,
    getProcessUnsavedNewName,
    getProcessVersionId,
    isProcessRenamed,
    isSaveDisabled,
} from "../../../../reducers/selectors/graph";
import { getCapabilities } from "../../../../reducers/selectors/other";
import { useWindows, WindowKind } from "../../../../windowManager";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import type { ToolbarButtonProps } from "../../types";
function SaveButton(props: ToolbarButtonProps): JSX.Element {
    const { t } = useTranslation();
    const { disabled, type } = props;
    const capabilities = useSelector(getCapabilities);
    const saveDisabled = useSelector(isSaveDisabled);

    const processName = useSelector(getProcessName);
    const processVersionId = useSelector(getProcessVersionId);
    const unsavedNewName = useSelector(getProcessUnsavedNewName);
    const isRenamed = useSelector(isProcessRenamed);
    const title = isRenamed
        ? t("saveProcess.renameTitle", "Save scenario as {{name}}", { name: unsavedNewName })
        : t("saveProcess.title", "Save scenario {{name}}", { name: processName });

    const { open, confirm } = useWindows();
    const onClick = async () => {
        await HttpService.validateProcessVersion(processName, processVersionId).then((res) => {
            if (!res.data.isLatest) {
                confirm({
                    text: t(
                        "panels.actions.confirm-unsafe-save.message",
                        `Your local scenario version #${processVersionId} is outdated.
                        There is newer version #${res.data.latestVersion} created by ${res.data.modifiedBy} available. Are you sure you want to override it?`,
                    ),
                    confirmText: t("panels.actions.confirm-unsafe-save.confirmButton", "Confirm"),
                    denyText: t("panels.actions.confirm-unsafe-save.cancelButton", "Cancel"),
                    onConfirmCallback: (confirmed) => {
                        if (confirmed) {
                            open({
                                title,
                                isModal: true,
                                shouldCloseOnEsc: true,
                                kind: WindowKind.saveProcess,
                            });
                        }
                    },
                    width: window.innerWidth / 3,
                });
            } else {
                open({
                    title,
                    isModal: true,
                    shouldCloseOnEsc: true,
                    kind: WindowKind.saveProcess,
                });
            }
        });
    };

    const available = !disabled && !saveDisabled && capabilities.write;

    return (
        <ToolbarButton
            name={saveDisabled ? t("panels.actions.process-save.button", "save") : t("panels.actions.process-save.buttonUnsaved", "save*")}
            icon={<Icon />}
            disabled={!available}
            onClick={onClick}
            type={type}
        />
    );
}

export default SaveButton;
