import React from "react";
import { useTranslation } from "react-i18next";

import { loadProcessState } from "../../../../actions/nk/process";
import Icon from "../../../../assets/img/toolbarButtons/run-off-schedule.svg";
import HttpService from "../../../../http/HttpService/instance";
import type { RootState } from "../../../../reducers";
import { getProcessName, getProcessVersionId, isRunOffScheduleVisible } from "../../../../reducers/selectors/graph";
import { isRunOffSchedulePossible, isValidationResultPresent } from "../../../../reducers/selectors/graph2";
import { getCapabilities } from "../../../../reducers/selectors/other";
import { getProcessState } from "../../../../reducers/selectors/scenarioState";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import { ACTION_DIALOG_WIDTH } from "../../../../stylesheets/variables";
import { useWindows } from "../../../../windowManager/useWindows";
import { WindowKind } from "../../../../windowManager/WindowKind";
import type { ToggleProcessActionModalData } from "../../../modals/DeployProcessDialog";
import ProcessStateUtils from "../../../Process/ProcessStateUtils";
import { PredefinedActionName } from "../../../Process/types";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons/ToolbarButton";
import type { ToolbarButtonProps } from "../../types";

export default function RunOffScheduleButton(props: ToolbarButtonProps) {
    const { t } = useTranslation();
    const dispatch = useAppDispatch();
    const { disabled, type, titleOverride } = props;
    const scenarioState = useAppSelector((state: RootState) => getProcessState(state));
    const validationResultPresent = useAppSelector(isValidationResultPresent);
    const isVisible = useAppSelector(isRunOffScheduleVisible);
    const isPossible = useAppSelector(isRunOffSchedulePossible);
    const processName = useAppSelector(getProcessName);
    const processVersionId = useAppSelector(getProcessVersionId);
    const capabilities = useAppSelector(getCapabilities);
    const available = validationResultPresent && !disabled && isPossible && capabilities.deploy;

    const { open } = useWindows();

    const action = (comment: string) => {
        const name = processName;
        const versionId = processVersionId;
        return HttpService.runOffSchedule(name, comment).finally(() => dispatch(loadProcessState(name, versionId)));
    };
    const message = t("panels.actions.run-of-out-schedule.dialog", "Perform single execution", { name: processName });

    const defaultTooltip = t("panels.actions.run-off-schedule.tooltip", "run now");
    const tooltip =
        titleOverride ?? ProcessStateUtils.getActionCustomTooltip(scenarioState, PredefinedActionName.RunOffSchedule) ?? defaultTooltip;

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
