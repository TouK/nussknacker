import React from "react";
import { useTranslation } from "react-i18next";

import { layout } from "../../../../actions/nk/ui/layout";
import Icon from "../../../../assets/img/toolbarButtons/layout.svg";
import { useAppDispatch } from "../../../../store/storeHelpers";
import { useGraph } from "../../../graph/GraphContext";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import type { ToolbarButtonProps } from "../../types";

function LayoutButton(props: ToolbarButtonProps) {
    const dispatch = useAppDispatch();
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
