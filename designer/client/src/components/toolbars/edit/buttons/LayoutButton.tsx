import React from "react";
import { useThunkDispatch } from "../../../../store/configureStore";
import { layout } from "../../../../actions/nk";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import { useTranslation } from "react-i18next";
import { useGraph } from "../../../graph/GraphContext";
import Icon from "../../../../assets/img/toolbarButtons/layout.svg";
import { ToolbarButtonProps } from "../../types";

function LayoutButton(props: ToolbarButtonProps) {
    const dispatch = useThunkDispatch();
    const { t } = useTranslation();
    const graphGetter = useGraph();
    const { disabled, type } = props;

    return (
        <CapabilitiesToolbarButton
            editFrontend
            name={t("panels.actions.edit-layout.button", "layout")}
            icon={<Icon />}
            disabled={disabled}
            onClick={(e) => {
                const altMode = "altKey" in e && e.altKey === true;
                dispatch(layout(() => graphGetter?.()?.forceLayout(altMode)));
            }}
            type={type}
        />
    );
}

export default LayoutButton;
