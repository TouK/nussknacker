import React from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";

import { hideRunProcessDetails } from "../../../../actions/nk";
import Icon from "../../../../assets/img/toolbarButtons/hide.svg";
import { getIsTestingMode } from "../../../../reducers/selectors/graph";
import { useAppDispatch } from "../../../../store/configureStore";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import type { ToolbarButtonProps } from "../../types";

function HideButton(props: ToolbarButtonProps) {
    const { disabled, type } = props;
    const dispatch = useAppDispatch();
    const isTestingMode = useSelector(getIsTestingMode);
    const { t } = useTranslation();
    return (
        <ToolbarButton
            name={t("panels.actions.test-hide.button.name", "hide")}
            title={t("panels.actions.test-hide.button.title", "hide counts")}
            icon={<Icon />}
            disabled={disabled || !isTestingMode}
            onClick={() => dispatch(hideRunProcessDetails())}
            type={type}
        />
    );
}

export default HideButton;
