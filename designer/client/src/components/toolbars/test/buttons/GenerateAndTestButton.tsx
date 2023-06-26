import React from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import Icon from "../../../../assets/img/toolbarButtons/generate-and-test.svg";
import { getTestCapabilities, isLatestProcessVersion } from "../../../../reducers/selectors/graph";
import { useWindows } from "../../../../windowManager";
import { WindowKind } from "../../../../windowManager/WindowKind";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import { ToolbarButtonProps } from "../../types";
import UrlIcon from "../../../UrlIcon";

type Props = ToolbarButtonProps;

function GenerateAndTestButton(props: Props) {
    const { disabled } = props;
    const { t } = useTranslation();
    const testCapabilities = useSelector(getTestCapabilities);
    const processIsLatestVersion = useSelector(isLatestProcessVersion);
    const available = !disabled && processIsLatestVersion && testCapabilities && testCapabilities.canGenerateTestData;
    const { open } = useWindows();

    return (
        <CapabilitiesToolbarButton
            write
            name={props.name || t("panels.actions.generate-and-test.button.name", "generated")}
            title={props.title || t("panels.actions.generate-and-test.button.title", "run test on generated data")}
            icon={<UrlIcon src={props.icon} FallbackComponent={Icon} />}
            disabled={!available}
            onClick={() =>
                open({
                    kind: WindowKind.generateDataAndTest,
                })
            }
        />
    );
}

export default GenerateAndTestButton;
