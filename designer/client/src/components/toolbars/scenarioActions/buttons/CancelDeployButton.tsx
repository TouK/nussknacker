import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { loadProcessState } from "../../../../actions/nk/process";
import Icon from "../../../../assets/img/toolbarButtons/stop.svg";
import { useUserSettings } from "../../../../common/useUserSettings";
import HttpService from "../../../../http/HttpService/instance";
import { getProcessName, getProcessVersionId, isCancelPossible } from "../../../../reducers/selectors/graph";
import { getCapabilities } from "../../../../reducers/selectors/other";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import { ACTION_DIALOG_WIDTH } from "../../../../stylesheets/variables";
import { useWindows } from "../../../../windowManager/useWindows";
import { WindowKind } from "../../../../windowManager/WindowKind";
import type { ToggleProcessActionModalData } from "../../../modals/DeployProcessDialog";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons/ToolbarButton";
import type { ToolbarButtonProps } from "../../types";

type CancelPresetValue = "cancel" | "cancelWithComment";

interface CancelDeployPreset {
    value: CancelPresetValue;
    label: string;
    isDisabled?: boolean;
}

export default function CancelDeployButton(props: ToolbarButtonProps) {
    const [allowQuickCancelDeploy] = useUserSettings("scenario.allowQuickCancelDeploy");

    const { t } = useTranslation();
    const dispatch = useAppDispatch();
    const { disabled, type, title } = props;
    const cancelPossible = useAppSelector(isCancelPossible);
    const processName = useAppSelector(getProcessName);
    const processVersionId = useAppSelector(getProcessVersionId);
    const capabilities = useAppSelector(getCapabilities);
    const available = !disabled && cancelPossible && capabilities.deploy;

    const [isCancelCallProcessing, setIsCancelCallProcessing] = useState(false);

    const { open } = useWindows();
    const action = useCallback(
        (comment?: string) =>
            HttpService.cancel(processName, comment).finally(() => dispatch(loadProcessState(processName, processVersionId))),
        [dispatch, processName, processVersionId],
    );
    const message = t("panels.actions.deploy-cancel.dialog", "Stop scenario {{name}}", { name: processName });

    const handleCancelDeploy = useCallback(async () => {
        try {
            setIsCancelCallProcessing(true);
            await action();
        } finally {
            setIsCancelCallProcessing(false);
        }
    }, [action]);

    const handleOpenCancelDeployDialog = useCallback(
        () =>
            open<ToggleProcessActionModalData>({
                title: message,
                kind: WindowKind.deployProcess,
                width: ACTION_DIALOG_WIDTH,
                meta: { action },
            }),
        [action, message, open],
    );

    const presets = useMemo<CancelDeployPreset[]>(
        () => [
            { value: "cancel", label: t("panels.actions.deploy-stop.button", "stop"), isDisabled: !available },
            {
                value: "cancelWithComment",
                label: t("panels.actions.deploy-stop.stopWithComment", "stop with comment"),
                isDisabled: !available,
            },
        ],
        [available, t],
    );

    const handlePresetChange = useCallback(
        async (preset: CancelDeployPreset) => {
            switch (preset.value) {
                case "cancel": {
                    await handleCancelDeploy();
                    break;
                }
                case "cancelWithComment": {
                    await handleOpenCancelDeployDialog();
                    break;
                }
            }
        },
        [handleCancelDeploy, handleOpenCancelDeployDialog],
    );

    if (allowQuickCancelDeploy) {
        return (
            <ToolbarButton
                name={t("panels.actions.deploy-stop.button", "stop")}
                disabled={!available || isCancelCallProcessing}
                isLoading={isCancelCallProcessing}
                icon={<Icon />}
                title={title}
                onClick={handleCancelDeploy}
                presets={presets}
                onPresetChange={handlePresetChange}
                type={type}
            />
        );
    }

    return (
        <ToolbarButton
            name={t("panels.actions.deploy-cancel.button", "cancel")}
            disabled={!available}
            icon={<Icon />}
            title={title}
            onClick={handleOpenCancelDeployDialog}
            type={type}
        />
    );
}
