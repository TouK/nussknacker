import React from "react";
import { useTranslation } from "react-i18next";
import { useDispatch } from "react-redux";

import { zoomIn } from "../../../../actions/nk";
import Icon from "../../../../assets/img/toolbarButtons/zoom-in.svg";
import { useGraph } from "../../../graph/GraphContext";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import type { ToolbarButtonProps } from "../../types";

export function ZoomInButton(props: ToolbarButtonProps) {
    const { t } = useTranslation();
    const dispatch = useDispatch();
    const graphGetter = useGraph();
    const { disabled, type } = props;
    const available = !disabled && graphGetter?.();

    return (
        <ToolbarButton
            name={t("panels.actions.view-zoomIn.label", "zoom-in")}
            icon={<Icon />}
            disabled={!available}
            onClick={() => dispatch(zoomIn(graphGetter?.()))}
            type={type}
        />
    );
}
