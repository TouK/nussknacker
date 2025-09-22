import React from "react";
import { useTranslation } from "react-i18next";

import Icon from "../../../../assets/img/toolbarButtons/JSON.svg";
import ProcessUtils from "../../../../common/ProcessUtils";
import HttpService from "../../../../http/HttpService/instance";
import { getProcessName, getProcessVersionId, getScenarioGraph } from "../../../../reducers/selectors/graph";
import { useAppSelector } from "../../../../store/storeHelpers";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import type { ToolbarButtonProps } from "../../types";

export default function ExportButton(props: ToolbarButtonProps) {
    const { disabled, type } = props;
    const versionId = useAppSelector(getProcessVersionId);
    const scenarioName = useAppSelector(getProcessName);
    const scenarioGraph = useAppSelector(getScenarioGraph);
    const canExport = useAppSelector(ProcessUtils.canExport);

    const available = !disabled && canExport;
    const { t } = useTranslation();

    return (
        <ToolbarButton
            name={t("panels.actions.process-export.button", "export")}
            icon={<Icon />}
            disabled={!available}
            onClick={() => {
                HttpService.exportProcess(scenarioName, scenarioGraph, versionId);
            }}
            type={type}
        />
    );
}
