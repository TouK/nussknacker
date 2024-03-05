import React from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import { getProcessName, getScenarioGraph, getTestCapabilities } from "../../../../reducers/selectors/graph";
import Icon from "../../../../assets/img/toolbarButtons/from-file.svg";
import { ToolbarButtonProps } from "../../types";
import { testProcessFromFile } from "../../../../actions/nk/displayTestResults";

function FromFileButton(props: ToolbarButtonProps) {
    const dispatch = useDispatch();
    const testCapabilities = useSelector(getTestCapabilities);
    const processName = useSelector(getProcessName);
    const scenarioGraph = useSelector(getScenarioGraph);
    const { disabled } = props;
    const { t } = useTranslation();

    const available = !disabled && testCapabilities && testCapabilities.canBeTested;

    return (
        <CapabilitiesToolbarButton
            write
            name={t("panels.actions.test-from-file.button.name", "from file")}
            title={t("panels.actions.test-from-file.button.title", "run test on data from file")}
            icon={<Icon />}
            disabled={!available}
            onDrop={(files) => files.forEach((file) => dispatch(testProcessFromFile(processName, file, scenarioGraph)))}
        />
    );
}

export default FromFileButton;
