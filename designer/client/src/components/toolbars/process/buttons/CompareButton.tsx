import React from "react";
import { useTranslation } from "react-i18next";

import Icon from "../../../../assets/img/toolbarButtons/compare.svg";
import { hasOneVersion } from "../../../../reducers/selectors/graph";
import { useAppSelector } from "../../../../store/storeHelpers";
import { useWindows } from "../../../../windowManager/useWindows";
import { handleOpenCompareVersionDialog } from "../../../modals/CompareVersionsDialog";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons/ToolbarButton";
import type { ToolbarButtonProps } from "../../types";

type Props = ToolbarButtonProps;

function CompareButton(props: Props): React.JSX.Element {
    const { disabled, type } = props;
    const isSingleVersion = useAppSelector(hasOneVersion);
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
