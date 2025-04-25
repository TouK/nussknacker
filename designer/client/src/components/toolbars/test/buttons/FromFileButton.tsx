import React from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";

import { testProcessFromFile } from "../../../../actions/nk/displayTestResults";
import Icon from "../../../../assets/img/toolbarButtons/from-file.svg";
import { TestCapabilityStatus } from "../../../../common/TestResultUtils";
import { getProcessName, getScenarioGraph, getTestCapabilities } from "../../../../reducers/selectors/graph";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import type { ToolbarButtonProps } from "../../types";

function FromFileButton(props: ToolbarButtonProps) {
    const dispatch = useDispatch();
    const testCapabilities = useSelector(getTestCapabilities);
    const processName = useSelector(getProcessName);
    const scenarioGraph = useSelector(getScenarioGraph);
    const { disabled, type } = props;
    const { t } = useTranslation();

    const available = !disabled && testCapabilities && testCapabilities.testWithGeneratedData.status == TestCapabilityStatus.AVAILABLE;

    return (
        <CapabilitiesToolbarButton
            write
            name={t("panels.actions.test-from-file.button.name", "from file")}
            title={t("panels.actions.test-from-file.button.title", "run test on data from file")}
            icon={<Icon />}
            disabled={!available}
            onDrop={(files) => files.forEach((file) => dispatch(testProcessFromFile(processName, file, scenarioGraph)))}
            type={type}
        />
    );
}

export default FromFileButton;
