import React from "react";
import { RootState } from "../../../../reducers";
import ProcessUtils from "../../../../common/ProcessUtils";
import { connect } from "react-redux";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { getProcessVersionId, getScenario } from "../../../../reducers/selectors/graph";
import { useTranslation } from "react-i18next";
import Icon from "../../../../assets/img/toolbarButtons/JSON.svg";
import { ToolbarButtonProps } from "../../types";
import HttpService from "../../../../http/HttpService";

type Props = StateProps & ToolbarButtonProps;

function JSONButton(props: Props) {
    const { scenario, versionId, canExport, disabled } = props;
    const available = !disabled && canExport;
    const { t } = useTranslation();

    return (
        <ToolbarButton
            name={t("panels.actions.process-JSON.button", "JSON")}
            icon={<Icon />}
            disabled={!available}
            onClick={() => {
                HttpService.exportProcess(scenario, versionId);
            }}
        />
    );
}

const mapState = (state: RootState) => {
    return {
        versionId: getProcessVersionId(state),
        scenario: getScenario(state),
        canExport: ProcessUtils.canExport(state),
    };
};

const mapDispatch = {};

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>;

export default connect(mapState, mapDispatch)(JSONButton);
