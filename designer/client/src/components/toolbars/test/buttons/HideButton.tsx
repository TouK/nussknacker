import React from "react";
import { useTranslation } from "react-i18next";

import { hideRunProcessDetails } from "../../../../actions/nk/process";
import Icon from "../../../../assets/img/toolbarButtons/hide.svg";
import { getIsTestingMode } from "../../../../reducers/selectors/testing";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons/ToolbarButton";
import type { ToolbarButtonProps } from "../../types";

function HideButton(props: ToolbarButtonProps) {
    const { disabled, type } = props;
    const dispatch = useAppDispatch();
    const isTestingMode = useAppSelector(getIsTestingMode);
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
