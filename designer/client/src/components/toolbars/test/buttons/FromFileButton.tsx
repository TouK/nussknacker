import React from "react";
import { useTranslation } from "react-i18next";

import { testProcessFromFile } from "../../../../actions/nk/testingActions";
import Icon from "../../../../assets/img/toolbarButtons/from-file.svg";
import { TestCapabilityStatus } from "../../../../common/TestResultUtils";
import { getTestCapabilities } from "../../../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import type { ToolbarButtonProps } from "../../types";

function FromFileButton(props: ToolbarButtonProps) {
    const dispatch = useAppDispatch();
    const testCapabilities = useAppSelector(getTestCapabilities);
    const { disabled, type } = props;
    const { t } = useTranslation();

    const available = !disabled && testCapabilities?.testWithLiveData.status === TestCapabilityStatus.AVAILABLE;

    return (
        <CapabilitiesToolbarButton
            write
            name={t("panels.actions.test-from-file.button.name", "from file")}
            title={t("panels.actions.test-from-file.button.title", "run test on data from file")}
            icon={<Icon />}
            disabled={!available}
            onDrop={(files) => files.forEach((file) => dispatch(testProcessFromFile(file)))}
            type={type}
        />
    );
}

export default FromFileButton;
