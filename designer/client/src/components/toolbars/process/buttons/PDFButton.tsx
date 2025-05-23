import React from "react";
import { useTranslation } from "react-i18next";
import { connect } from "react-redux";

import Icon from "../../../../assets/img/toolbarButtons/PDF.svg";
import { canExport } from "../../../../common/ProcessUtils2";
import HttpService from "../../../../http/HttpService";
import type { RootState } from "../../../../reducers";
import { getProcessName, getProcessVersionId } from "../../../../reducers/selectors/graph";
import { useGraph } from "../../../graph/GraphContext";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import type { ToolbarButtonProps } from "../../types";

type Props = StateProps & ToolbarButtonProps;

function PDFButton(props: Props) {
    const { processName, versionId, canExport, disabled, type } = props;
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

const mapState = (state: RootState) => {
    return {
        processName: getProcessName(state),
        versionId: getProcessVersionId(state),
        canExport: canExport(state),
    };
};

const mapDispatch = {};
type StateProps = typeof mapDispatch & ReturnType<typeof mapState>;

export default connect(mapState, mapDispatch)(PDFButton);
