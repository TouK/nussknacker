import React from "react";
import { useTranslation } from "react-i18next";
import { connect } from "react-redux";

import Icon from "../../../../assets/img/toolbarButtons/JSON.svg";
import HttpService from "../../../../http/HttpService";
import type { RootState } from "../../../../reducers";
import { canExport, getProcessName, getProcessVersionId, getScenarioGraph } from "../../../../reducers/selectors/graph";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import type { ToolbarButtonProps } from "../../types";

type Props = StateProps & ToolbarButtonProps;

function ExportButton(props: Props) {
    const { scenarioName, scenarioGraph, versionId, canExport, disabled, type } = props;
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

const mapState = (state: RootState) => {
    return {
        versionId: getProcessVersionId(state),
        scenarioName: getProcessName(state),
        scenarioGraph: getScenarioGraph(state),
        canExport: canExport(state),
    };
};

const mapDispatch = {};

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>;

export default connect(mapState, mapDispatch)(ExportButton);
