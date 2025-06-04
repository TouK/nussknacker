import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";

import { disableToolTipsHighlight, enableToolTipsHighlight, fetchProcessToDisplay, loadProcessState } from "../../../../actions/nk";
import notificationActions from "../../../../actions/notificationActions";
import Icon from "../../../../assets/img/toolbarButtons/redeploy.svg";
import { useUserSettings } from "../../../../common/userSettings";
import type { NodesDeploymentData, ScenarioGraphSource } from "../../../../http/HttpService";
import HttpService from "../../../../http/HttpService";
import {
    getProcessName,
    getProcessVersionId,
    getScenarioGraphSource,
    hasError,
    isRedeployPossible,
    isRedeployVisible,
    isSaveDisabled,
    isValidationResultPresent,
} from "../../../../reducers/selectors/graph";
import { getCapabilities } from "../../../../reducers/selectors/other";
import { getIsRedeploying } from "../../../../reducers/selectors/scenarioState";
import { ACTION_DIALOG_WIDTH } from "../../../../stylesheets/variables";
import { useWindows, WindowKind } from "../../../../windowManager";
import type { ToggleProcessActionModalData } from "../../../modals/DeployProcessDialog";
import type { ProcessName, ProcessVersionId } from "../../../Process/types";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import type { ToolbarButtonProps } from "../../types";
import { ScenarioActionResultType } from "./types";

type RedeployPresetValue = "stopAndStart" | "configureAndStart";

interface RedeployPreset {
    value: RedeployPresetValue;
    label: string;
    isDisabled?: boolean;
}

export default function RedeployButton(props: ToolbarButtonProps) {
    const [settings] = useUserSettings();
    const allowQuickRedeploy = settings["scenario.allowQuickDeploy"];

    const dispatch = useDispatch();
    const isVisible = useSelector(isRedeployVisible);
    const isPossible = useSelector(isRedeployPossible);
    const saveDisabled = useSelector(isSaveDisabled);
    const hasErrors = useSelector(hasError);
    const validationResultPresent = useSelector(isValidationResultPresent);
    const processName = useSelector(getProcessName);
    const processVersionId = useSelector(getProcessVersionId);
    const capabilities = useSelector(getCapabilities);
    const isRedeploying = useSelector(getIsRedeploying);
    const scenarioGraphSource = useSelector(getScenarioGraphSource);

    const { disabled, type } = props;

    const [isRedeployCallProcessing, setIsRedeployCallProcessing] = useState(false);

    const isLoading = useMemo(() => isRedeploying || isRedeployCallProcessing, [isRedeployCallProcessing, isRedeploying]);

    const available = validationResultPresent && !disabled && isPossible && capabilities.deploy;
    const { t } = useTranslation();
    const deployToolTip = !capabilities.deploy
        ? t("panels.actions.redeploy.tooltips.forbidden", "Redeploy forbidden for current scenario.")
        : hasErrors
        ? t("panels.actions.redeploy.tooltips.error", "Cannot redeploy due to errors. Please look at the left panel for more details.")
        : !saveDisabled
        ? t("panels.actions.redeploy.tooltips.unsaved", "You have unsaved changes.")
        : null;
    const deployMouseOver = hasErrors ? () => dispatch(enableToolTipsHighlight()) : null;
    const deployMouseOut = hasErrors ? () => dispatch(disableToolTipsHighlight()) : null;

    const { open, confirm } = useWindows();

    const message = t("panels.actions.redeploy.dialog", "Redeploy scenario {{name}}", { name: processName });
    const action = useCallback(
        async (
            name: ProcessName,
            versionId: ProcessVersionId,
            comment: string,
            nodesDeploymentData?: NodesDeploymentData,
            scenarioGraphSource?: ScenarioGraphSource,
        ) => {
            const result = await HttpService.redeploy(name, comment, nodesDeploymentData, scenarioGraphSource);
            if (result.scenarioActionResultType === ScenarioActionResultType.DeploySuccess) {
                dispatch(fetchProcessToDisplay(name, result.deployedScenarioVersionId));
            } else {
                dispatch(loadProcessState(name, versionId));
            }
            return result;
        },
        [dispatch],
    );

    const handleValidateScenarioVersion = useCallback(
        async (callback: () => Promise<void>) => {
            await HttpService.validateProcessVersion(processName, processVersionId).then(async (res) => {
                if (!res.data.isLatest) {
                    await confirm({
                        text: t(
                            `You're currently checked out on version #{{localVersion}} and there is newer version
                             #{{latestVersion}} created by {{modifyBy}} available. Scenario will be deployed using 
                             version #{{versionToDeploy}}. Are you sure you want to perform this action?`,
                            {
                                latestVersion: res.data.latestVersion,
                                modifyBy: res.data.modifiedBy,
                                versionToDeploy: settings["toolbar.autoSaveDuringDeployRedeploy"]
                                    ? res.data.localVersion
                                    : res.data.latestVersion,
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
        [confirm, processName, processVersionId, t, settings],
    );

    const handleRedeploy = useCallback(async () => {
        try {
            setIsRedeployCallProcessing(true);
            const response = await action(processName, processVersionId, "", null, scenarioGraphSource);
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
            setIsRedeployCallProcessing(false);
        }
    }, [action, dispatch, processName, processVersionId, setIsRedeployCallProcessing, scenarioGraphSource]);

    const presets = useMemo<RedeployPreset[]>(
        () => [
            { value: "stopAndStart", label: "stop & start", isDisabled: !available },
            { value: "configureAndStart", label: "configure & start", isDisabled: !available },
        ],
        [available],
    );

    const handleOpenRedeployDialog = useCallback(async () => {
        await handleValidateScenarioVersion(async () => {
            await open<ToggleProcessActionModalData>({
                title: message,
                kind: WindowKind.deployWithParameters,
                width: ACTION_DIALOG_WIDTH,
                meta: { action, displayWarnings: true, actionName: "REDEPLOY" },
            });
        });
    }, [action, handleValidateScenarioVersion, message, open]);

    const handlePresetChange = useCallback(
        async (preset: RedeployPreset) => {
            switch (preset.value) {
                case "stopAndStart": {
                    await handleRedeploy();
                    break;
                }
                case "configureAndStart": {
                    await handleOpenRedeployDialog();
                    break;
                }
            }
        },
        [handleOpenRedeployDialog, handleRedeploy],
    );

    if (isVisible) {
        return (
            <>
                {allowQuickRedeploy ? (
                    <ToolbarButton
                        name={t("panels.actions.update.button", "update")}
                        disabled={!available || isLoading}
                        isLoading={isLoading}
                        icon={<Icon />}
                        title={deployToolTip}
                        onClick={handleRedeploy}
                        onMouseOver={deployMouseOver}
                        onMouseOut={deployMouseOut}
                        presets={presets}
                        type={type}
                        onPresetChange={handlePresetChange}
                    />
                ) : (
                    <ToolbarButton
                        name={t("panels.actions.redeploy.button", "redeploy")}
                        disabled={!available}
                        icon={<Icon />}
                        title={deployToolTip}
                        onClick={handleOpenRedeployDialog}
                        onMouseOver={deployMouseOver}
                        onMouseOut={deployMouseOut}
                        type={type}
                    />
                )}
            </>
        );
    } else return <></>;
}
