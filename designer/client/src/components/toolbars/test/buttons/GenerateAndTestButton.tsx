import React from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import Icon from "../../../../assets/img/toolbarButtons/generate-and-test.svg";
import { getTestCapabilities, isLatestProcessVersion } from "../../../../reducers/selectors/graph";
import { useWindows, WindowKind } from "../../../../windowManager";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import { ToolbarButtonProps } from "../../types";

type Props = ToolbarButtonProps;

function GenerateAndTestButton(props: Props) {
    const { disabled, type } = props;
    const { t } = useTranslation();
    const testCapabilities = useSelector(getTestCapabilities);
    const processIsLatestVersion = useSelector(isLatestProcessVersion);
    const available = !disabled && processIsLatestVersion && testCapabilities && testCapabilities.canGenerateTestData;
    const { open } = useWindows();

    return (
        <CapabilitiesToolbarButton
            write
            name={t("panels.actions.generate-and-test.button.name", "generated")}
            title={t("panels.actions.generate-and-test.button.title", "run test on generated data")}
            icon={<Icon />}
            disabled={!available}
            onClick={() =>
                open({
                    kind: WindowKind.generateDataAndTest,
                })
            }
            type={type}
        />
    );
}

export default GenerateAndTestButton;
