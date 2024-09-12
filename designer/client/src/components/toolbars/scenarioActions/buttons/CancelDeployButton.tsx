import React from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { loadProcessState } from "../../../../actions/nk";
import Icon from "../../../../assets/img/toolbarButtons/stop.svg";
import HttpService from "../../../../http/HttpService";
import { getProcessName, isCancelPossible } from "../../../../reducers/selectors/graph";
import { getCapabilities } from "../../../../reducers/selectors/other";
import { WindowKind, useWindows } from "../../../../windowManager";
import { ToggleProcessActionModalData } from "../../../modals/DeployProcessDialog";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { ToolbarButtonProps } from "../../types";
import { ACTION_DIALOG_WIDTH } from "../../../../stylesheets/variables";
import { ProcessName, ProcessVersionId } from "../../../Process/types";

export default function CancelDeployButton(props: ToolbarButtonProps) {
    const { t } = useTranslation();
    const dispatch = useDispatch();
    const { disabled, type } = props;
    const cancelPossible = useSelector(isCancelPossible);
    const processName = useSelector(getProcessName);
    const capabilities = useSelector(getCapabilities);
    const available = !disabled && cancelPossible && capabilities.deploy;

    const { open } = useWindows();
    const action = (name: ProcessName, versionId: ProcessVersionId, comment: string) =>
        HttpService.cancel(name, comment).finally(() => dispatch(loadProcessState(name, versionId)));
    const message = t("panels.actions.deploy-canel.dialog", "Cancel scenario {{name}}", { name: processName });

    return (
        <ToolbarButton
            name={t("panels.actions.deploy-canel.button", "cancel")}
            disabled={!available}
            icon={<Icon />}
            onClick={() =>
                open<ToggleProcessActionModalData>({
                    title: message,
                    kind: WindowKind.deployProcess,
                    width: ACTION_DIALOG_WIDTH,
                    meta: { action, activityName: "CANCEL" },
                })
            }
            type={type}
        />
    );
}
