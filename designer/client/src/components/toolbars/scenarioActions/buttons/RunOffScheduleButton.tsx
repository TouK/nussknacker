import React from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { loadProcessState } from "../../../../actions/nk";
import Icon from "../../../../assets/img/toolbarButtons/run-off-schedule.svg";
import HttpService from "../../../../http/HttpService";
import {
    getProcessName,
    isRunOffSchedulePossible,
    isRunOffScheduleVisible,
    isValidationResultPresent,
} from "../../../../reducers/selectors/graph";
import { getCapabilities } from "../../../../reducers/selectors/other";
import { useWindows, WindowKind } from "../../../../windowManager";
import { ToggleProcessActionModalData } from "../../../modals/DeployProcessDialog";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { ToolbarButtonProps } from "../../types";
import { ACTION_DIALOG_WIDTH } from "../../../../stylesheets/variables";
import ProcessStateUtils from "../../../Process/ProcessStateUtils";
import { RootState } from "../../../../reducers";
import { getProcessState } from "../../../../reducers/selectors/scenarioState";
import { PredefinedActionName, ProcessName, ProcessVersionId } from "../../../Process/types";

export default function RunOffScheduleButton(props: ToolbarButtonProps) {
    const { t } = useTranslation();
    const dispatch = useDispatch();
    const { disabled, type } = props;
    const scenarioState = useSelector((state: RootState) => getProcessState(state));
    const validationResultPresent = useSelector(isValidationResultPresent);
    const isVisible = useSelector(isRunOffScheduleVisible);
    const isPossible = useSelector(isRunOffSchedulePossible);
    const processName = useSelector(getProcessName);
    const capabilities = useSelector(getCapabilities);
    const available = validationResultPresent && !disabled && isPossible && capabilities.deploy;

    const { open } = useWindows();
    const action = (name: ProcessName, versionId: ProcessVersionId, comment: string) =>
        HttpService.runOffSchedule(name, comment).finally(() => dispatch(loadProcessState(name, versionId)));
    const message = t("panels.actions.run-of-out-schedule.dialog", "Perform single execution", { name: processName });

    const defaultTooltip = t("panels.actions.run-off-schedule.tooltip", "run now");
    const tooltip = ProcessStateUtils.getActionCustomTooltip(scenarioState, PredefinedActionName.RunOffSchedule) ?? defaultTooltip;

    if (isVisible) {
        return (
            <ToolbarButton
                name={t("panels.actions.run-off-schedule.button", "run now")}
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
