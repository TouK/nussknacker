import React from "react";
import { RootState } from "../../../../reducers/index";
import ProcessUtils from "../../../../common/ProcessUtils";
import { connect } from "react-redux";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { getScenarioName, getScenarioVersionId } from "../../../../reducers/selectors/graph";
import { useTranslation } from "react-i18next";
import { useGraph } from "../../../graph/GraphContext";
import Icon from "../../../../assets/img/toolbarButtons/PDF.svg";
import { ToolbarButtonProps } from "../../types";
import HttpService from "../../../../http/HttpService";

type Props = StateProps & ToolbarButtonProps;

function PDFButton(props: Props) {
    const { processName, versionId, canExport, disabled } = props;
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
        />
    );
}

const mapState = (state: RootState) => {
    return {
        processName: getScenarioName(state),
        versionId: getScenarioVersionId(state),
        canExport: ProcessUtils.canExport(state),
    };
};

const mapDispatch = {};
type StateProps = typeof mapDispatch & ReturnType<typeof mapState>;

export default connect(mapState, mapDispatch)(PDFButton);
