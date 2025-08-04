import React from "react";
import { useTranslation } from "react-i18next";

import { zoomOut } from "../../../../actions/nk";
import Icon from "../../../../assets/img/toolbarButtons/zoom-out.svg";
import { useAppDispatch } from "../../../../store/storeHelpers";
import { useGraph } from "../../../graph/GraphContext";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import type { ToolbarButtonProps } from "../../types";

export function ZoomOutButton(props: ToolbarButtonProps) {
    const { t } = useTranslation();
    const dispatch = useAppDispatch();
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
