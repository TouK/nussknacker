import React from "react";
import { useTranslation } from "react-i18next";
import { RootState } from "../../../../reducers/index";
import { connect } from "react-redux";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import { getProcessName, getScenarioGraph, getTestCapabilities } from "../../../../reducers/selectors/graph";
import Icon from "../../../../assets/img/toolbarButtons/from-file.svg";
import { ToolbarButtonProps } from "../../types";
import { testProcessFromFile } from "../../../../actions/nk/displayTestResults";

type Props = StateProps & ToolbarButtonProps;

function FromFileButton(props: Props) {
    const { processName, processToDisplay, testCapabilities, disabled } = props;
    const { testProcessFromFile } = props;
    const { t } = useTranslation();
    const available = !disabled && testCapabilities && testCapabilities.canBeTested;

    return (
        <CapabilitiesToolbarButton
            write
            name={t("panels.actions.test-from-file.button.name", "from file")}
            title={t("panels.actions.test-from-file.button.title", "run test on data from file")}
            icon={<Icon />}
            disabled={!available}
            onDrop={(files) => files.forEach((file) => testProcessFromFile(processName, file, processToDisplay))}
        />
    );
}

const mapState = (state: RootState) => ({
    testCapabilities: getTestCapabilities(state),
    processName: getProcessName(state),
    processToDisplay: getScenarioGraph(state),
});

const mapDispatch = {
    testProcessFromFile,
};

export type StateProps = typeof mapDispatch & ReturnType<typeof mapState>;

export default connect(mapState, mapDispatch)(FromFileButton);
