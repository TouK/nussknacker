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
import { useGraph } from "../../../graph/GraphContext";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import { BuiltinButtonTypes } from "../../../toolbarSettings/buttons/buttonsMap";
import type { ToolbarButtonProps } from "../../types";

function LayoutButton(props: ToolbarButtonProps) {
    const dispatch = useAppDispatch();
    const { t } = useTranslation();
    const graphGetter = useGraph();
    const { disabled, type } = props;

    return (
        <CapabilitiesToolbarButton
            editFrontend
            name={props.type === BuiltinButtonTypes.editLayout ? t("panels.actions.edit-layout.button", "layout") : props.type}
            icon={
                props.type === BuiltinButtonTypes.alignHorizontalLeft ? (
                    <AlignLeft />
                ) : props.type === BuiltinButtonTypes.alignHorizontalCenter ? (
                    <AlignHCenter />
                ) : props.type === BuiltinButtonTypes.alignHorizontalRight ? (
                    <AlignRight />
                ) : props.type === BuiltinButtonTypes.alignVerticalTop ? (
                    <AlignTop />
                ) : props.type === BuiltinButtonTypes.alignVerticalCenter ? (
                    <AlignVCenter />
                ) : props.type === BuiltinButtonTypes.alignVerticalBottom ? (
                    <AlignBottom />
                ) : props.type === BuiltinButtonTypes.distributeVertical ? (
                    <DistributeV />
                ) : props.type === BuiltinButtonTypes.distributeHorizontal ? (
                    <DistributeH />
                ) : (
                    <Icon />
                )
            }
            disabled={disabled}
            onClick={(e) => {
                const altMode = "altKey" in e && e.altKey === true;
                dispatch(
                    layout(() =>
                        graphGetter?.()?.forceLayout({
                            readOnly: altMode,
                            align: {
                                horizontal:
                                    props.type === BuiltinButtonTypes.alignHorizontalLeft
                                        ? "left"
                                        : props.type === BuiltinButtonTypes.alignHorizontalCenter
                                        ? "center"
                                        : props.type === BuiltinButtonTypes.alignHorizontalRight
                                        ? "right"
                                        : null,
                                vertical:
                                    props.type === BuiltinButtonTypes.alignVerticalTop
                                        ? "top"
                                        : props.type === BuiltinButtonTypes.alignVerticalCenter
                                        ? "center"
                                        : props.type === BuiltinButtonTypes.alignVerticalBottom
                                        ? "bottom"
                                        : null,
                            },
                            distribute: {
                                horizontal: props.type === BuiltinButtonTypes.distributeHorizontal,
                                vertical: props.type === BuiltinButtonTypes.distributeVertical,
                            },
                        }),
                    ),
                );
            }}
            type={type}
        />
    );
}

export default LayoutButton;
