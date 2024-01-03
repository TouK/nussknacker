import { isEmpty } from "lodash";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import Icon from "../../../../assets/img/toolbarButtons/migrate.svg";
import * as DialogMessages from "../../../../common/DialogMessages";
import HttpService from "../../../../http/HttpService";
import { getProcessName, getProcessVersionId, isMigrationPossible } from "../../../../reducers/selectors/graph";
import { getFeatureSettings, getTargetEnvironmentId } from "../../../../reducers/selectors/settings";
import { useWindows } from "../../../../windowManager";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import { ToolbarButtonProps } from "../../types";

type Props = ToolbarButtonProps;

function MigrateButton(props: Props) {
    const { disabled } = props;
    const processName = useSelector(getProcessName);
    const versionId = useSelector(getProcessVersionId);
    const featuresSettings = useSelector(getFeatureSettings);
    const migrationPossible = useSelector(isMigrationPossible);
    const targetEnvironmentId = useSelector(getTargetEnvironmentId);

    const available = !disabled && migrationPossible;
    const { t } = useTranslation();
    const { confirm } = useWindows();

    const onClick = useCallback(
        () =>
            confirm({
                text: DialogMessages.migrate(processName, targetEnvironmentId),
                onConfirmCallback: (confirmed) => confirmed && HttpService.migrateProcess(processName, versionId),
                confirmText: t("panels.actions.process-migrate.yes", "Yes"),
                denyText: t("panels.actions.process-migrate.no", "No"),
            }),
        [confirm, processName, t, targetEnvironmentId, versionId],
    );

    if (isEmpty(featuresSettings?.remoteEnvironment)) {
        return null;
    }

    return (
        <CapabilitiesToolbarButton
            deploy
            name={t("panels.actions.process-migrate.button", "migrate")}
            icon={<Icon />}
            disabled={!available}
            onClick={onClick}
        />
    );
}

export default MigrateButton;
