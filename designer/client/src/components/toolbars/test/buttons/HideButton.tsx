import React from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { hideRunProcessDetails } from "../../../../actions/nk";
import { getShowRunProcessDetails } from "../../../../reducers/selectors/graph";
import Icon from "../../../../assets/img/toolbarButtons/hide.svg";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { ToolbarButtonProps } from "../../types";

function HideButton(props: ToolbarButtonProps) {
    const { disabled, type } = props;
    const dispatch = useDispatch();
    const showRunProcessDetails = useSelector(getShowRunProcessDetails);
    const available = !disabled && showRunProcessDetails;
    const { t } = useTranslation();
    return (
        <ToolbarButton
            name={t("panels.actions.test-hide.button.name", "hide")}
            title={t("panels.actions.test-hide.button.title", "hide counts")}
            icon={<Icon />}
            disabled={!available}
            onClick={() => dispatch(hideRunProcessDetails())}
            type={type}
        />
    );
}

export default HideButton;
