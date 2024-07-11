import React from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import Icon from "../../../../assets/img/toolbarButtons/generate.svg";
import { getTestCapabilities, isLatestProcessVersion } from "../../../../reducers/selectors/graph";
import { useWindows, WindowKind } from "../../../../windowManager";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import { ToolbarButtonProps } from "../../types";

type Props = ToolbarButtonProps;

function GenerateButton(props: Props) {
    const { disabled, type } = props;
    const { t } = useTranslation();
    const testCapabilities = useSelector(getTestCapabilities);
    const processIsLatestVersion = useSelector(isLatestProcessVersion);
    const available = !disabled && processIsLatestVersion && testCapabilities && testCapabilities.canGenerateTestData;
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
