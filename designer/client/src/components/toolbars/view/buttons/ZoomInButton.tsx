import React from "react";
import { useTranslation } from "react-i18next";

import { zoomIn } from "../../../../actions/nk/zoom";
import Icon from "../../../../assets/img/toolbarButtons/zoom-in.svg";
import { useAppDispatch } from "../../../../store/storeHelpers";
import { useGraph } from "../../../graph/GraphContext";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons/ToolbarButton";
import type { ToolbarButtonProps } from "../../types";

function ZoomInButton(props: ToolbarButtonProps) {
    const { t } = useTranslation();
    const dispatch = useAppDispatch();
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

export default ZoomInButton;
