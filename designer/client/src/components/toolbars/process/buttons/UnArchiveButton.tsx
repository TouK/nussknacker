import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import Icon from "../../../../assets/img/toolbarButtons/unarchive.svg";
import * as DialogMessages from "../../../../common/DialogMessages";
import HttpService from "../../../../http/HttpService";
import { getProcessName, isArchived } from "../../../../reducers/selectors/graph";
import { useWindows } from "../../../../windowManager";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import { ToolbarButtonProps } from "../../types";
import { displayCurrentProcessVersion, loadProcessToolbarsConfiguration } from "../../../../actions/nk";
import { getScenarioActivities } from "../../../../actions/nk/scenarioActivities";

function UnArchiveButton({ disabled, type }: ToolbarButtonProps) {
    const processName = useSelector(getProcessName);
    const archived = useSelector(isArchived);
    const available = !disabled || !archived;
    const { t } = useTranslation();
    const { confirm } = useWindows();
    const dispatch = useDispatch();

    const onClick = useCallback(
        () =>
            available &&
            confirm({
                text: DialogMessages.unArchiveProcess(processName),
                onConfirmCallback: (confirmed) =>
                    confirmed &&
                    HttpService.unArchiveProcess(processName).then(async () => {
                        dispatch(loadProcessToolbarsConfiguration(processName));
                        dispatch(displayCurrentProcessVersion(processName));
                        await dispatch(await getScenarioActivities(processName));
                    }),
                confirmText: t("panels.actions.process-unarchive.yes", "Yes"),
                denyText: t("panels.actions.process-unarchive.no", "No"),
            }),
        [available, confirm, dispatch, processName, t],
    );

    return (
        <CapabilitiesToolbarButton
            change
            name={t("panels.actions.process-unarchive.button", "unarchive")}
            icon={<Icon />}
            disabled={!available}
            onClick={onClick}
            type={type}
        />
    );
}

export default UnArchiveButton;
