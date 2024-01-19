import React from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { disableToolTipsHighlight, enableToolTipsHighlight, loadProcessState } from "../../../../actions/nk";
import Icon from "../../../../assets/img/toolbarButtons/deploy.svg";
import HttpService from "../../../../http/HttpService";
import { getProcessName, hasError, isDeployPossible, isSaveDisabled } from "../../../../reducers/selectors/graph";
import { getCapabilities } from "../../../../reducers/selectors/other";
import { useWindows } from "../../../../windowManager";
import { WindowKind } from "../../../../windowManager";
import { ToggleProcessActionModalData } from "../../../modals/DeployProcessDialog";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { ToolbarButtonProps } from "../../types";

export default function DeployButton(props: ToolbarButtonProps) {
    const dispatch = useDispatch();
    const deployPossible = useSelector(isDeployPossible);
    const saveDisabled = useSelector(isSaveDisabled);
    const hasErrors = useSelector(hasError);
    const processName = useSelector(getProcessName);
    const capabilities = useSelector(getCapabilities);
    const { disabled } = props;

    const available = !disabled && deployPossible && capabilities.deploy;

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

    const { open } = useWindows();

    const message = t("panels.actions.deploy.dialog", "Deploy scenario {{name}}", { name: processName });
    const action = (p, c) => HttpService.deploy(p, c).finally(() => dispatch(loadProcessState(processName)));

    return (
        <ToolbarButton
            name={t("panels.actions.deploy.button", "deploy")}
            disabled={!available}
            icon={<Icon />}
            title={deployToolTip}
            onClick={() =>
                open<ToggleProcessActionModalData>({
                    title: message,
                    kind: WindowKind.deployProcess,
                    meta: { action, displayWarnings: true },
                })
            }
            onMouseOver={deployMouseOver}
            onMouseOut={deployMouseOut}
        />
    );
}
