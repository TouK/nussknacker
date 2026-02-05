import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { disableToolTipsHighlight, enableToolTipsHighlight } from "../../../../actions/nk/tooltips";
import notificationActions from "../../../../actions/notificationActions";
import Icon from "../../../../assets/img/toolbarButtons/deploy.svg";
import { useUserSettings } from "../../../../common/useUserSettings";
import HttpService from "../../../../http/HttpService/instance";
import type { NodesDeploymentData } from "../../../../http/HttpService/types";
import { getProcessName, getProcessVersionId, isDeployVisible } from "../../../../reducers/selectors/graph";
import { hasError, isDeployPossible, isValidationResultPresent } from "../../../../reducers/selectors/graph2";
import { getCapabilities } from "../../../../reducers/selectors/other";
import { getIsDeploying } from "../../../../reducers/selectors/scenarioState";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import { ACTION_DIALOG_WIDTH } from "../../../../stylesheets/variables";
import { useWindows } from "../../../../windowManager/useWindows";
import { WindowKind } from "../../../../windowManager/WindowKind";
import type { ToggleProcessActionModalData } from "../../../modals/DeployProcessDialog";
import { PredefinedActionName } from "../../../Process/types";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons/ToolbarButton";
import type { ToolbarButtonProps } from "../../types";
import { deployAction } from "./deployActions";
import { ScenarioActionResultType } from "./types";

type DeployPresetValue = "start" | "configureAndStart";

interface DeployPreset {
    value: DeployPresetValue;
    label: string;
    isDisabled?: boolean;
}

export default function DeployButton(props: ToolbarButtonProps) {
    const [allowQuickDeploy, autoSaveDuringDeployRedeploy] = useUserSettings(
        "scenario.allowQuickDeploy",
        "toolbar.autoSaveDuringDeployRedeploy",
    );

    const dispatch = useAppDispatch();

    const isVisible = useAppSelector(isDeployVisible);
    const isPossible = useAppSelector(isDeployPossible);
    const hasErrors = useAppSelector(hasError);
    const validationResultPresent = useAppSelector(isValidationResultPresent);
    const processName = useAppSelector(getProcessName);
    const processVersionId = useAppSelector(getProcessVersionId);
    const capabilities = useAppSelector(getCapabilities);
    const isDeploying = useAppSelector(getIsDeploying);

    const { disabled, type, titleOverride } = props;

    const [isDeployCallProcessing, setIsDeployCallProcessing] = useState(false);

    const isLoading = useMemo(() => isDeploying || isDeployCallProcessing, [isDeployCallProcessing, isDeploying]);

    const available = validationResultPresent && !disabled && isPossible && capabilities.deploy;
    const { t } = useTranslation();
    const deployToolTip =
        titleOverride ??
        (!capabilities.deploy
            ? t("panels.actions.deploy.tooltips.forbidden", "Deploy forbidden for current scenario.")
            : hasErrors
            ? t("panels.actions.deploy.tooltips.error", "Cannot deploy due to errors. Please look at the left panel for more details.")
            : null);
    const deployMouseOver = hasErrors ? () => dispatch(enableToolTipsHighlight()) : null;
    const deployMouseOut = hasErrors ? () => dispatch(disableToolTipsHighlight()) : null;

    const { open, confirm } = useWindows();

    const message = t("panels.actions.deploy.dialog", "Deploy scenario {{name}}", { name: processName });
    const action = useCallback(
        async (comment?: string, nodesDeploymentData?: NodesDeploymentData) => dispatch(deployAction(comment, nodesDeploymentData)),
        [dispatch],
    );

    const handleValidateScenarioVersion = useCallback(
        async (callback: () => Promise<void>) => {
            await HttpService.validateProcessVersion(processName, processVersionId).then(async (res) => {
                if (!res.data.isLatest) {
                    await confirm({
                        text: t(
                            "panels.actions.confirm-unsafe-deployment.message",
                            `You're currently checked out on version #{{localVersion}} and there is newer version
                             #{{latestVersion}} created by {{modifyBy}} available. Scenario will be deployed using 
                             version #{{versionToDeploy}}. Are you sure you want to perform this action?`,
                            {
                                latestVersion: res.data.latestVersion,
                                modifyBy: res.data.modifiedBy,
                                versionToDeploy: autoSaveDuringDeployRedeploy ? res.data.localVersion : res.data.latestVersion,
                                localVersion: res.data.localVersion,
                            },
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
        [autoSaveDuringDeployRedeploy, confirm, processName, processVersionId, t],
    );

    const handleDeploy = useCallback(async () => {
        try {
            dispatch({ type: "PENDING_SCENARIO_ACTION", action: PredefinedActionName.Deploy });
            setIsDeployCallProcessing(true);
            const response = await action();
            switch (response.scenarioActionResultType) {
                case ScenarioActionResultType.DeploySuccess:
                case ScenarioActionResultType.Success:
                case ScenarioActionResultType.UnhandledError:
                    break;
                case ScenarioActionResultType.ValidationError:
                    dispatch(notificationActions.error(response.msg));
                    break;
            }
        } finally {
            setIsDeployCallProcessing(false);
        }
    }, [action, dispatch, setIsDeployCallProcessing]);

    const presets = useMemo<DeployPreset[]>(
        () => [
            { value: "start", label: "start", isDisabled: !available },
            { value: "configureAndStart", label: "configure & start", isDisabled: !available },
        ],
        [available],
    );

    const handleOpenDeployDialog = useCallback(async () => {
        await handleValidateScenarioVersion(async () => {
            await open<ToggleProcessActionModalData>({
                title: message,
                kind: WindowKind.deployWithParameters,
                width: ACTION_DIALOG_WIDTH,
                meta: { action, displayWarnings: true, actionName: "DEPLOY" },
            });
        });
    }, [action, handleValidateScenarioVersion, message, open]);

    const handlePresetChange = useCallback(
        async (preset: DeployPreset) => {
            switch (preset.value) {
                case "start": {
                    await handleDeploy();
                    break;
                }
                case "configureAndStart": {
                    await handleOpenDeployDialog();
                    break;
                }
            }
        },
        [handleDeploy, handleOpenDeployDialog],
    );

    if (isVisible) {
        return (
            <>
                {allowQuickDeploy ? (
                    <ToolbarButton
                        name={t("panels.actions.start.button", "start")}
                        disabled={!available || isLoading}
                        isLoading={isLoading}
                        icon={<Icon />}
                        title={deployToolTip}
                        onClick={handleDeploy}
                        onMouseOver={deployMouseOver}
                        onMouseOut={deployMouseOut}
                        type={type}
                        presets={presets}
                        onPresetChange={handlePresetChange}
                    />
                ) : (
                    <ToolbarButton
                        name={t("panels.actions.deploy.button", "deploy")}
                        disabled={!available || isLoading}
                        isLoading={isLoading}
                        icon={<Icon />}
                        title={deployToolTip}
                        onClick={handleOpenDeployDialog}
                        onMouseOver={deployMouseOver}
                        onMouseOut={deployMouseOut}
                        type={type}
                    />
                )}
            </>
        );
    } else return <></>;
}
