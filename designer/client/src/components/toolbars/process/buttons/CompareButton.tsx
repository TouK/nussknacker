import React from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import Icon from "../../../../assets/img/toolbarButtons/compare.svg";
import { hasOneVersion } from "../../../../reducers/selectors/graph";
import { useWindows } from "../../../../windowManager";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { ToolbarButtonProps } from "../../types";
import { handleOpenCompareVersionDialog } from "../../../modals/CompareVersionsDialog";

type Props = ToolbarButtonProps;

function CompareButton(props: Props): JSX.Element {
    const { disabled, type } = props;
    const isSingleVersion = useSelector(hasOneVersion);
    const available = !disabled && !isSingleVersion;
    const { t } = useTranslation();
    const { open } = useWindows();

    return (
        <ToolbarButton
            name={t("panels.actions.process-compare.button", "compare")}
            icon={<Icon />}
            disabled={!available}
            onClick={() => open(handleOpenCompareVersionDialog())}
            type={type}
        />
    );
}

export default CompareButton;
