import React from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";

import { loadProcessState } from "../../../../actions/nk";
import Icon from "../../../../assets/img/toolbarButtons/stop.svg";
import HttpService from "../../../../http/HttpService";
import { getProcessName, isCancelPossible } from "../../../../reducers/selectors/graph";
import { getCapabilities } from "../../../../reducers/selectors/other";
import { ACTION_DIALOG_WIDTH } from "../../../../stylesheets/variables";
import { WindowKind, useWindows } from "../../../../windowManager";
import type { ToggleProcessActionModalData } from "../../../modals/DeployProcessDialog";
import type { ProcessName, ProcessVersionId } from "../../../Process/types";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import type { ToolbarButtonProps } from "../../types";

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
    const message = t("panels.actions.deploy-canel.dialog", "Stop scenario {{name}}", { name: processName });

    return (
        <ToolbarButton
            name={t("panels.actions.deploy-canel.button", "stop")}
            disabled={!available}
            icon={<Icon />}
            onClick={() =>
                open<ToggleProcessActionModalData>({
                    title: message,
                    kind: WindowKind.deployProcess,
                    width: ACTION_DIALOG_WIDTH,
                    meta: { action },
                })
            }
            type={type}
        />
    );
}
