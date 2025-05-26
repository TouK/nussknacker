import React, { useCallback, useMemo } from "react";
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
import { useSaveScenario } from "../../../modals/saveScenario/useSaveScenario";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import type { ToolbarButtonProps } from "../../types";

type SavePresetValue = "save" | "SaveWithComment";

interface SavePreset {
    value: SavePresetValue;
    label: string;
    isDisabled?: boolean;
}

function SaveButton(props: ToolbarButtonProps): JSX.Element {
    const { handleSaveScenarioAction } = useSaveScenario();
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
    const handleValidateScenarioVersion = useCallback(
        async (callback: () => Promise<void>) => {
            await HttpService.validateProcessVersion(processName, processVersionId).then(async (res) => {
                if (!res.data.isLatest) {
                    await confirm({
                        text: t(
                            "panels.actions.confirm-unsafe-save.message",
                            `Your local scenario version #{{processVersionId}} is outdated.
                        There is newer version #{{latestVersion}} created by {{modifyBy}} available. Are you sure you want to override it?`,
                            { processVersionId, latestVersion: res.data.latestVersion, modifyBy: res.data.modifiedBy },
                        ),
                        confirmText: t("panels.actions.confirm-unsafe-save.confirmButton", "Confirm"),
                        denyText: t("panels.actions.confirm-unsafe-save.cancelButton", "Cancel"),
                        onConfirmCallback: async (confirmed) => {
                            if (confirmed) {
                                await callback();
                            }
                        },
                        width: window.innerWidth / 3,
                    });
                } else {
                    await callback();
                }
            });
        },
        [confirm, processName, processVersionId, t],
    );

    const unsavedChanges = !saveDisabled;
    const available = !disabled && unsavedChanges && capabilities.write;

    const presets = useMemo<SavePreset[]>(
        () => [
            { value: "save", label: "Save", isDisabled: !available },
            { value: "SaveWithComment", label: "Save with comment", isDisabled: !available },
        ],
        [available],
    );

    const handleSaveScenarioActionWithValidation = useCallback(async () => {
        await handleValidateScenarioVersion(async () => {
            await handleSaveScenarioAction();
        });
    }, [handleSaveScenarioAction, handleValidateScenarioVersion]);

    const handlePresetChange = useCallback(
        async (preset: SavePreset) => {
            switch (preset.value) {
                case "save": {
                    await handleSaveScenarioActionWithValidation();
                    break;
                }
                case "SaveWithComment": {
                    await handleValidateScenarioVersion(async () => {
                        await open({
                            title,
                            isModal: true,
                            shouldCloseOnEsc: true,
                            kind: WindowKind.saveProcess,
                        });
                    });
                    break;
                }
            }
        },
        [handleSaveScenarioActionWithValidation, handleValidateScenarioVersion, open, title],
    );

    return (
        <ToolbarButton
            name={t("panels.actions.process-save.button", "save")}
            showIndicator={unsavedChanges}
            icon={<Icon />}
            disabled={!available}
            onClick={handleSaveScenarioActionWithValidation}
            type={type}
            presets={presets}
            selected={presets[0]}
            onPresetChange={handlePresetChange}
        />
    );
}

export default SaveButton;
