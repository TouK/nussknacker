import React from "react";
import { useTranslation } from "react-i18next";

import Icon from "../../../../assets/img/toolbarButtons/PDF.svg";
import ProcessUtils from "../../../../common/ProcessUtils";
import HttpService from "../../../../http/HttpService";
import { getProcessName, getProcessVersionId } from "../../../../reducers/selectors/graph";
import { useAppSelector } from "../../../../store/configureStore";
import { useGraph } from "../../../graph/GraphContext";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import type { ToolbarButtonProps } from "../../types";

export default function PDFButton(props: ToolbarButtonProps) {
    const { disabled, type } = props;
    const processName = useAppSelector(getProcessName);
    const versionId = useAppSelector(getProcessVersionId);
    const canExport = useAppSelector(ProcessUtils.canExport);

    const available = !disabled && canExport;
    const { t } = useTranslation();
    const graphGetter = useGraph();

    return (
        <ToolbarButton
            name={t("panels.actions.process-PDF.button", "PDF")}
            icon={<Icon />}
            disabled={!available}
            onClick={async () => {
                // TODO: add busy indicator
                // TODO: try to do this in worker
                // TODO: try to do this more in redux/react style
                const exportedGraph = await graphGetter?.()?.exportGraph();
                HttpService.exportProcessToPdf(processName, versionId, exportedGraph);
            }}
            type={type}
        />
    );
}
