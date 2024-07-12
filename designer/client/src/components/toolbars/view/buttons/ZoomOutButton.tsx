import { useTranslation } from "react-i18next";
import { useDispatch } from "react-redux";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { zoomOut } from "../../../../actions/nk";
import React from "react";
import { useGraph } from "../../../graph/GraphContext";
import Icon from "../../../../assets/img/toolbarButtons/zoom-out.svg";
import { ToolbarButtonProps } from "../../types";

export function ZoomOutButton(props: ToolbarButtonProps) {
    const { t } = useTranslation();
    const dispatch = useDispatch();
    const graphGetter = useGraph();
    const { disabled, type } = props;
    const available = !disabled && graphGetter?.();

    return (
        <ToolbarButton
            name={t("panels.actions.view-zoomOut.label", "zoom-out")}
            icon={<Icon />}
            disabled={!available}
            onClick={() => dispatch(zoomOut(graphGetter?.()))}
            type={type}
        />
    );
}
