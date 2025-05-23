import React from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";

import { disableToolTipsHighlight, enableToolTipsHighlight, loadProcessState } from "../../../../actions/nk";
import Icon from "../../../../assets/img/toolbarButtons/redeploy.svg";
import type { NodesDeploymentData } from "../../../../http/HttpService";
import HttpService from "../../../../http/HttpService";
import {
    getProcessName,
    getProcessVersionId,
    hasError,
    isRedeployPossible,
    isRedeployVisible,
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

export default function RedeployButton(props: ToolbarButtonProps) {
    const dispatch = useDispatch();
    const isVisible = useSelector(isRedeployVisible);
    const isPossible = useSelector(isRedeployPossible);
    const saveDisabled = useSelector(isSaveDisabled);
    const hasErrors = useSelector(hasError);
    const validationResultPresent = useSelector(isValidationResultPresent);
    const processName = useSelector(getProcessName);
    const processVersionId = useSelector(getProcessVersionId);
    const capabilities = useSelector(getCapabilities);
    const { disabled, type } = props;

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
    const action = (name: ProcessName, versionId: ProcessVersionId, comment: string, nodesDeploymentData?: NodesDeploymentData) =>
        HttpService.redeploy(name, comment, nodesDeploymentData).finally(() => dispatch(loadProcessState(name, versionId)));

    const handleOnClick = async () => {
        await HttpService.validateProcessVersion(processName, processVersionId).then((res) => {
            if (!res.data.isLatest) {
                confirm({
                    text: t(
                        "panels.actions.confirm-unsafe-deployment.message",
                        `There is newer version #${res.data.latestVersion} created by ${res.data.modifiedBy} available. Scenario will be deployed using the newest version.
                         You're currently checked out on version #${res.data.localVersion}. 
                         Are you sure you want to perform this action?`,
                    ),
                    confirmText: t("panels.actions.confirm-unsafe-deployment.confirmButton", "Confirm"),
                    denyText: t("panels.actions.confirm-unsafe-deployment.cancelButton", "Cancel"),
                    onConfirmCallback: (confirmed) => {
                        if (confirmed) {
                            open<ToggleProcessActionModalData>({
                                title: message,
                                kind: WindowKind.deployWithParameters,
                                width: ACTION_DIALOG_WIDTH,
                                meta: { action, displayWarnings: true, actionName: "REDEPLOY" },
                            });
                        }
                    },
                    width: window.innerWidth / 3,
                });
            } else {
                open<ToggleProcessActionModalData>({
                    title: message,
                    kind: WindowKind.deployWithParameters,
                    width: ACTION_DIALOG_WIDTH,
                    meta: { action, displayWarnings: true, actionName: "REDEPLOY" },
                });
            }
        });
    };

    if (isVisible) {
        return (
            <ToolbarButton
                name={t("panels.actions.redeploy.button", "redeploy")}
                disabled={!available}
                icon={<Icon />}
                title={deployToolTip}
                onClick={handleOnClick}
                onMouseOver={deployMouseOver}
                onMouseOut={deployMouseOut}
                type={type}
            />
        );
    } else return <></>;
}
