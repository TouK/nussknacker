import React from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";

import Icon from "../../../../assets/img/toolbarButtons/JSON.svg";
import ProcessUtils from "../../../../common/ProcessUtils";
import HttpService from "../../../../http/HttpService";
import { getProcessName, getProcessVersionId, getScenarioGraph } from "../../../../reducers/selectors/graph";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import type { ToolbarButtonProps } from "../../types";

export default function ExportButton(props: ToolbarButtonProps) {
    const { disabled, type } = props;
    const versionId = useSelector(getProcessVersionId);
    const scenarioName = useSelector(getProcessName);
    const scenarioGraph = useSelector(getScenarioGraph);
    const canExport = useSelector(ProcessUtils.canExport);

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
