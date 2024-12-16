import React from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { loadProcessState } from "../../../../actions/nk";
import Icon from "../../../../assets/img/toolbarButtons/run-off-schedule.svg";
import HttpService from "../../../../http/HttpService";
import {
    getProcessName,
    getProcessVersionId,
    isRunOutOfSchedulePossible,
    isRunOutOfScheduleVisible,
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
import { PredefinedActionName } from "../../../Process/types";

export default function RunOutOfScheduleButton(props: ToolbarButtonProps) {
    const { t } = useTranslation();
    const dispatch = useDispatch();
    const { disabled, type } = props;
    const scenarioState = useSelector((state: RootState) => getProcessState(state));
    const isVisible = useSelector(isRunOutOfScheduleVisible);
    const isPossible = useSelector(isRunOutOfSchedulePossible);
    const processName = useSelector(getProcessName);
    const processVersionId = useSelector(getProcessVersionId);
    const capabilities = useSelector(getCapabilities);
    const available = !disabled && isPossible && capabilities.deploy;

    const { open } = useWindows();
    const action = (p, c) => HttpService.runOutOfSchedule(p, c).finally(() => dispatch(loadProcessState(processName, processVersionId)));
    const message = t("panels.actions.run-of-out-schedule.dialog", "Perform single execution", { name: processName });

    const defaultTooltip = t("panels.actions.run-off-schedule.tooltip", "run now");
    const tooltip = ProcessStateUtils.getActionCustomTooltip(scenarioState, PredefinedActionName.RunOutOfSchedule) ?? defaultTooltip;

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
