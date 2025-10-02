import React from "react";
import { useTranslation } from "react-i18next";

import { layout } from "../../../../actions/nk/ui/layout";
import Icon from "../../../../assets/img/toolbarButtons/layout.svg";
import AlignHCenter from "../../../../assets/img/toolbarButtons/layout_ahc.svg";
import AlignLeft from "../../../../assets/img/toolbarButtons/layout_ahl.svg";
import AlignRight from "../../../../assets/img/toolbarButtons/layout_ahr.svg";
import AlignBottom from "../../../../assets/img/toolbarButtons/layout_avb.svg";
import AlignVCenter from "../../../../assets/img/toolbarButtons/layout_avc.svg";
import AlignTop from "../../../../assets/img/toolbarButtons/layout_avt.svg";
import DistributeH from "../../../../assets/img/toolbarButtons/layout_dh.svg";
import DistributeV from "../../../../assets/img/toolbarButtons/layout_dv.svg";
import { useAppDispatch } from "../../../../store/storeHelpers";
import type { AlignCellsVariant } from "../../../graph/alignCells";
import { useGraph } from "../../../graph/GraphContext";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import { BuiltinButtonTypes } from "../../../toolbarSettings/buttons/buttonsMap";
import type { ToolbarButtonProps } from "../../types";

function getAlignCellsVariant(type: ToolbarButtonProps["type"]): AlignCellsVariant {
    switch (type) {
        case BuiltinButtonTypes.alignHorizontalLeft:
            return "left";
        case BuiltinButtonTypes.alignHorizontalRight:
            return "right";
        case BuiltinButtonTypes.alignVerticalTop:
            return "top";
        case BuiltinButtonTypes.alignVerticalBottom:
            return "bottom";
        case BuiltinButtonTypes.alignHorizontalCenter:
            return "center:horizontal";
        case BuiltinButtonTypes.alignVerticalCenter:
            return "center:vertical";
        case BuiltinButtonTypes.distributeHorizontal:
            return "distribute:horizontal";
        case BuiltinButtonTypes.distributeVertical:
            return "distribute:vertical";
    }
}

function getIcon(type: ToolbarButtonProps["type"]) {
    switch (type) {
        case BuiltinButtonTypes.alignHorizontalLeft:
            return <AlignLeft />;
        case BuiltinButtonTypes.alignHorizontalCenter:
            return <AlignHCenter />;
        case BuiltinButtonTypes.alignHorizontalRight:
            return <AlignRight />;
        case BuiltinButtonTypes.alignVerticalTop:
            return <AlignTop />;
        case BuiltinButtonTypes.alignVerticalCenter:
            return <AlignVCenter />;
        case BuiltinButtonTypes.alignVerticalBottom:
            return <AlignBottom />;
        case BuiltinButtonTypes.distributeVertical:
            return <DistributeV />;
        case BuiltinButtonTypes.distributeHorizontal:
            return <DistributeH />;
        default:
            return <Icon />;
    }
}

function LayoutButton(props: ToolbarButtonProps) {
    const dispatch = useAppDispatch();
    const { t } = useTranslation();
    const graphGetter = useGraph();
    const { disabled, type } = props;
    return (
        <CapabilitiesToolbarButton
            editFrontend
            name={props.type === BuiltinButtonTypes.editLayout ? t("panels.actions.edit-layout.button", "layout") : props.type}
            icon={getIcon(props.type)}
            disabled={disabled}
            onClick={(e) => {
                const altMode = "altKey" in e && e.altKey === true;
                const variant = getAlignCellsVariant(props.type);
                dispatch(layout(() => graphGetter?.()?.forceLayout(variant, altMode)));
            }}
            type={type}
        />
    );
}

export default LayoutButton;
