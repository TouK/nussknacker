import type { TFunction } from "i18next";
import React, { useCallback } from "react";
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
import { useRegisterCommands } from "../../../CommandBar/useRegisterCommands";
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

function getName(t: TFunction, type: ToolbarButtonProps["type"]) {
    switch (type) {
        case BuiltinButtonTypes.editLayout:
            return t("panels.actions.edit-layout.button", "layout");
        case BuiltinButtonTypes.alignHorizontalLeft:
            return t("panels.actions.align-left.button", "align left");
        case BuiltinButtonTypes.alignVerticalTop:
            return t("panels.actions.align-top.button", "align top");
        case BuiltinButtonTypes.alignHorizontalRight:
            return t("panels.actions.align-right.button", "align right");
        case BuiltinButtonTypes.alignVerticalBottom:
            return t("panels.actions.align-bottom.button", "align bottom");
        case BuiltinButtonTypes.alignHorizontalCenter:
            return t("panels.actions.center-horizontal.button", "center horizontal");
        case BuiltinButtonTypes.alignVerticalCenter:
            return t("panels.actions.center-vertical.button", "center vertical");
        case BuiltinButtonTypes.distributeHorizontal:
            return t("panels.actions.distribute-horizontal.button", "distribute horizontal");
        case BuiltinButtonTypes.distributeVertical:
            return t("panels.actions.distribute-vertical.button", "distribute vertical");
        default:
            return type;
    }
}

function getKey(type: ToolbarButtonProps["type"]) {
    switch (type) {
        case BuiltinButtonTypes.editLayout:
            return ["a", "x"];
        case BuiltinButtonTypes.alignHorizontalLeft:
            return ["a", "l"];
        case BuiltinButtonTypes.alignVerticalTop:
            return ["a", "t"];
        case BuiltinButtonTypes.alignHorizontalRight:
            return ["a", "r"];
        case BuiltinButtonTypes.alignVerticalBottom:
            return ["a", "b"];
        case BuiltinButtonTypes.alignHorizontalCenter:
            return ["a", "h"];
        case BuiltinButtonTypes.alignVerticalCenter:
            return ["a", "v"];
        case BuiltinButtonTypes.distributeHorizontal:
            return ["a", "d", "h"];
        case BuiltinButtonTypes.distributeVertical:
            return ["a", "d", "v"];
    }
}

function LayoutButton(props: ToolbarButtonProps) {
    const dispatch = useAppDispatch();
    const { t } = useTranslation();
    const graphGetter = useGraph();
    const { disabled, type } = props;
    const name = getName(t, props.type);
    const icon = getIcon(props.type);
    const performAction = useCallback(
        (altMode?: boolean) => {
            const variant = getAlignCellsVariant(props.type);
            dispatch(layout(() => graphGetter?.()?.forceLayout(variant, altMode)));
        },
        [dispatch, graphGetter, props.type],
    );

    useRegisterCommands(
        () => [
            {
                id: `align/${props.type}`,
                perform: () => performAction(),
                section: "actions",
                name,
                icon,
                shortcut: getKey(props.type),
            },
        ],
        [icon, name, performAction, props.type],
    );

    return (
        <CapabilitiesToolbarButton
            editFrontend
            name={name}
            icon={icon}
            disabled={disabled}
            onClick={(e) => {
                const altMode = "altKey" in e && e.altKey === true;
                performAction(altMode);
            }}
            type={type}
        />
    );
}

export default LayoutButton;
