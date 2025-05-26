import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";

import { disableToolTipsHighlight, enableToolTipsHighlight, loadProcessState } from "../../../../actions/nk";
import notificationActions from "../../../../actions/notificationActions";
import Icon from "../../../../assets/img/toolbarButtons/deploy.svg";
import type { NodesDeploymentData } from "../../../../http/HttpService";
import HttpService from "../../../../http/HttpService";
import {
    getProcessName,
    getProcessVersionId,
    hasError,
    isDeployPossible,
    isDeployVisible,
    isSaveDisabled,
    isValidationResultPresent,
} from "../../../../reducers/selectors/graph";
import { getCapabilities } from "../../../../reducers/selectors/other";
import { ACTION_DIALOG_WIDTH } from "../../../../stylesheets/variables";
import { useWindows } from "../../../../windowManager";
import { WindowKind } from "../../../../windowManager";
import type { ToggleProcessActionModalData } from "../../../modals/DeployProcessDialog";
import type { ProcessName, ProcessVersionId } from "../../../Process/types";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import type { ToolbarButtonProps } from "../../types";
import { ScenarioActionResultType } from "./types";

type DeployPresetValue = "start" | "configureAndStart";

interface DeployPreset {
    value: DeployPresetValue;
    label: string;
    isDisabled?: boolean;
}

export default function DeployButton(props: ToolbarButtonProps) {
    const dispatch = useDispatch();
    const isVisible = useSelector(isDeployVisible);
    const isPossible = useSelector(isDeployPossible);
    const saveDisabled = useSelector(isSaveDisabled);
    const hasErrors = useSelector(hasError);
    const validationResultPresent = useSelector(isValidationResultPresent);
    const processName = useSelector(getProcessName);
    const processVersionId = useSelector(getProcessVersionId);
    const capabilities = useSelector(getCapabilities);
    const { disabled, type } = props;

    const [isDeployProcessing, setIsDeployProcessing] = useState(false);

    const available = validationResultPresent && !disabled && isPossible && capabilities.deploy;
    const { t } = useTranslation();
    const deployToolTip = !capabilities.deploy
        ? t("panels.actions.deploy.tooltips.forbidden", "Deploy forbidden for current scenario.")
        : hasErrors
        ? t("panels.actions.deploy.tooltips.error", "Cannot deploy due to errors. Please look at the left panel for more details.")
        : !saveDisabled
        ? t("panels.actions.deploy.tooltips.unsaved", "You have unsaved changes.")
        : null;
    const deployMouseOver = hasErrors ? () => dispatch(enableToolTipsHighlight()) : null;
    const deployMouseOut = hasErrors ? () => dispatch(disableToolTipsHighlight()) : null;

    const { open, confirm } = useWindows();

    const message = t("panels.actions.deploy.dialog", "Deploy scenario {{name}}", { name: processName });
    const action = useCallback(
        (name: ProcessName, versionId: ProcessVersionId, comment: string, nodesDeploymentData?: NodesDeploymentData) =>
            HttpService.deploy(name, comment, nodesDeploymentData).finally(() => dispatch(loadProcessState(name, versionId))),
        [dispatch],
    );

    const handleValidateScenarioVersion = useCallback(
        async (callback: () => Promise<void>) => {
            await HttpService.validateProcessVersion(processName, processVersionId).then(async (res) => {
                if (!res.data.isLatest) {
                    await confirm({
                        text: t(
                            "panels.actions.confirm-unsafe-deployment.message",
                            `There is newer version #{{latestVersion}} created by {{modifyBy}} available. Scenario will be deployed using the newest version.
                         You're currently checked out on version #{{localVersion}}. 
                         Are you sure you want to perform this action?`,
                            { latestVersion: res.data.latestVersion, modifyBy: res.data.modifiedBy, localVersion: res.data.localVersion },
                        ),
                        confirmText: t("panels.actions.confirm-unsafe-deployment.confirmButton", "Confirm"),
                        denyText: t("panels.actions.confirm-unsafe-deployment.cancelButton", "Cancel"),
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

    const handleDeploy = useCallback(async () => {
        try {
            setIsDeployProcessing(true);
            const response = await action(processName, processVersionId, "");
            switch (response.scenarioActionResultType) {
                case ScenarioActionResultType.Success:
                case ScenarioActionResultType.UnhandledError:
                    break;
                case ScenarioActionResultType.ValidationError:
                    dispatch(notificationActions.error(response.msg));
                    break;
                default:
                    console.log("Unexpected result type:", response.scenarioActionResultType);
                    break;
            }
        } finally {
            setIsDeployProcessing(false);
        }
    }, [action, dispatch, processName, processVersionId, setIsDeployProcessing]);

    const presets = useMemo<DeployPreset[]>(
        () => [
            { value: "start", label: "start", isDisabled: !available },
            { value: "configureAndStart", label: "configure & start", isDisabled: !available },
        ],
        [available],
    );

    const handlePresetChange = useCallback(
        async (preset: DeployPreset) => {
            switch (preset.value) {
                case "start": {
                    await handleDeploy();
                    break;
                }
                case "configureAndStart": {
                    await handleValidateScenarioVersion(async () => {
                        await open<ToggleProcessActionModalData>({
                            title: message,
                            kind: WindowKind.deployWithParameters,
                            width: ACTION_DIALOG_WIDTH,
                            meta: { action, displayWarnings: true, actionName: "DEPLOY" },
                        });
                    });
                    break;
                }
            }
        },
        [action, handleDeploy, handleValidateScenarioVersion, message, open],
    );

    if (isVisible) {
        return (
            <ToolbarButton
                name={t("panels.actions.deploy.button", "start")}
                disabled={!available || isDeployProcessing}
                isLoading={isDeployProcessing}
                icon={<Icon />}
                title={deployToolTip}
                onClick={handleDeploy}
                onMouseOver={deployMouseOver}
                onMouseOut={deployMouseOut}
                type={type}
                presets={presets}
                onPresetChange={handlePresetChange}
            />
        );
    } else return <></>;
}
