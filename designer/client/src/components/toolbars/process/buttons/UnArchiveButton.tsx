import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";

import { loadProcessToolbarsConfiguration } from "../../../../actions/nk/loadProcessToolbarsConfiguration";
import { displayCurrentProcessVersion } from "../../../../actions/nk/process";
import { getScenarioActivities } from "../../../../actions/nk/scenarioActivities";
import Icon from "../../../../assets/img/toolbarButtons/unarchive.svg";
import DialogMessages from "../../../../common/DialogMessages";
import HttpService from "../../../../http/instance";
import { getProcessName, isArchived } from "../../../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import { useWindows } from "../../../../windowManager/useWindows";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import type { ToolbarButtonProps } from "../../types";

function UnArchiveButton({ disabled, type }: ToolbarButtonProps) {
    const processName = useAppSelector(getProcessName);
    const archived = useAppSelector(isArchived);
    const available = !disabled || !archived;
    const { t } = useTranslation();
    const { confirm } = useWindows();
    const dispatch = useAppDispatch();

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
