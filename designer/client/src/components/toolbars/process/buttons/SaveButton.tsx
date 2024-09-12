import React from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import Icon from "../../../../assets/img/toolbarButtons/save.svg";
import { getProcessName, getProcessUnsavedNewName, isProcessRenamed, isSaveDisabled } from "../../../../reducers/selectors/graph";
import { getCapabilities } from "../../../../reducers/selectors/other";
import { useWindows, WindowKind } from "../../../../windowManager";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { ToolbarButtonProps } from "../../types";
function SaveButton(props: ToolbarButtonProps): JSX.Element {
    const { t } = useTranslation();
    const { disabled, type } = props;
    const capabilities = useSelector(getCapabilities);
    const saveDisabled = useSelector(isSaveDisabled);

    const processName = useSelector(getProcessName);
    const unsavedNewName = useSelector(getProcessUnsavedNewName);
    const isRenamed = useSelector(isProcessRenamed);
    const title = isRenamed
        ? t("saveProcess.renameTitle", "Save scenario as", { name: unsavedNewName })
        : t("saveProcess.title", "Save scenario", { name: processName });

    const { open } = useWindows();
    const onClick = () =>
        open({
            title,
            isModal: true,
            shouldCloseOnEsc: true,
            kind: WindowKind.saveProcess,
        });

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
