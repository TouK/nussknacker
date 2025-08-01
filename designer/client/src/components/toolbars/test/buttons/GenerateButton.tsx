import React from "react";
import { useTranslation } from "react-i18next";

import Icon from "../../../../assets/img/toolbarButtons/generate.svg";
import { TestCapabilityStatus } from "../../../../common/TestResultUtils";
import { getTestCapabilities, isLatestProcessVersion } from "../../../../reducers/selectors/graph";
import { useAppSelector } from "../../../../store/storeHelpers";
import { useWindows, WindowKind } from "../../../../windowManager";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import type { ToolbarButtonProps } from "../../types";

type Props = ToolbarButtonProps;

function GenerateButton(props: Props) {
    const { disabled, type } = props;
    const { t } = useTranslation();
    const testCapabilities = useAppSelector(getTestCapabilities);
    const processIsLatestVersion = useAppSelector(isLatestProcessVersion);
    const available = !disabled && processIsLatestVersion && testCapabilities?.testWithLiveData.status === TestCapabilityStatus.AVAILABLE;
    const { open } = useWindows();

    return (
        <CapabilitiesToolbarButton
            write
            name={t("panels.actions.test-generate.button.name", "generate file")}
            title={t("panels.actions.test-generate.button.title", "generate test data file")}
            icon={<Icon />}
            disabled={!available}
            type={type}
            onClick={() =>
                open({
                    kind: WindowKind.generateTestData,
                })
            }
        />
    );
}

export default GenerateButton;
