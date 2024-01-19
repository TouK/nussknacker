import React from "react";
import { RootState } from "../../../../reducers";
import ProcessUtils from "../../../../common/ProcessUtils";
import { connect } from "react-redux";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { getProcessVersionId, getScenarioGraph, getProcessName } from "../../../../reducers/selectors/graph";
import { useTranslation } from "react-i18next";
import Icon from "../../../../assets/img/toolbarButtons/JSON.svg";
import { ToolbarButtonProps } from "../../types";
import HttpService from "../../../../http/HttpService";

type Props = StateProps & ToolbarButtonProps;

function JSONButton(props: Props) {
    const { scenarioName, scenarioGraph, versionId, canExport, disabled } = props;
    const available = !disabled && canExport;
    const { t } = useTranslation();

    return (
        <ToolbarButton
            name={t("panels.actions.process-JSON.button", "JSON")}
            icon={<Icon />}
            disabled={!available}
            onClick={() => {
                HttpService.exportProcess(scenarioName, scenarioGraph, versionId);
            }}
        />
    );
}

const mapState = (state: RootState) => {
    return {
        versionId: getProcessVersionId(state),
        scenarioName: getProcessName(state),
        scenarioGraph: getScenarioGraph(state),
        canExport: ProcessUtils.canExport(state),
    };
};

const mapDispatch = {};

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>;

export default connect(mapState, mapDispatch)(JSONButton);
