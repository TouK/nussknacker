import React from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { loadProcessState } from "../../../../actions/nk";
import Icon from "../../../../assets/img/toolbarButtons/perform-single-execution.svg";
import HttpService from "../../../../http/HttpService";
import { getProcessName, isPerformSingleExecutionPossible, isPerformSingleExecutionVisible } from "../../../../reducers/selectors/graph";
import { getCapabilities } from "../../../../reducers/selectors/other";
import { useWindows, WindowKind } from "../../../../windowManager";
import { ToggleProcessActionModalData } from "../../../modals/DeployProcessDialog";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { ToolbarButtonProps } from "../../types";
import { ACTION_DIALOG_WIDTH } from "../../../../stylesheets/variables";
import ProcessStateUtils from "../../../Process/ProcessStateUtils";
import { RootState } from "../../../../reducers";
import { getProcessState } from "../../../../reducers/selectors/scenarioState";
import { ActionTooltip, PredefinedActionName } from "../../../Process/types";

export default function PerformSingleExecutionButton(props: ToolbarButtonProps) {
    const { t } = useTranslation();
    const dispatch = useDispatch();
    const { disabled, type } = props;
    const scenarioState = useSelector((state: RootState) => getProcessState(state));
    const isVisible = useSelector(isPerformSingleExecutionVisible);
    const isPossible = useSelector(isPerformSingleExecutionPossible);
    const processName = useSelector(getProcessName);
    const capabilities = useSelector(getCapabilities);
    const available = !disabled && isPossible && capabilities.deploy;

    const { open } = useWindows();
    const action = (p, c) => HttpService.performSingleExecution(p, c).finally(() => dispatch(loadProcessState(processName)));
    const message = t("panels.actions.perform-single-execution.dialog", "Perform single execution", { name: processName });

    const actionTooltip = ProcessStateUtils.getActionCustomTooltip(scenarioState, PredefinedActionName.PerformSingleExecution);

    const deployedVersionDescription = scenarioState.deployedVersionId ? ` (version ${scenarioState.deployedVersionId} is deployed)` : ``;

    const tooltip =
        actionTooltip === ActionTooltip.NotAllowedForDeployedVersion
            ? t(
                  "panels.actions.perform-single-execution.tooltip.not-allowed-for-deployed-version",
                  "There is new version {{ latestVersion }} available.{{ deployedVersionDescription }}",
                  {
                      latestVersion: scenarioState.latestVersionId,
                      deployedVersionDescription: deployedVersionDescription,
                  },
              )
            : actionTooltip === ActionTooltip.NotAllowedInCurrentState
            ? t("panels.actions.perform-single-execution.tooltip.not-allowed-in-current-state", "Disabled for {{ status }} status.", {
                  status: scenarioState.status.name,
              })
            : "run now";

    if (isVisible) {
        return (
            <ToolbarButton
                name={t("panels.actions.perform-single-execution.button", "run now")}
                title={tooltip}
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
    } else return <></>;
}
