import React from "react";
import { useTranslation } from "react-i18next";
import LeftIcon from "../assets/img/arrows/arrow-left.svg";
import RightIcon from "../assets/img/arrows/arrow-right.svg";
import { styled } from "@mui/material";
import { PanelSide } from "./sidePanels/SidePanel";
import { PANEL_WIDTH } from "./sidePanels/SidePanelStyled";

interface IconWrapper {
    type: PanelSide;
    isOpened: boolean;
}
interface Props {
    isOpened: boolean;
    onToggle: () => void;
    type: PanelSide;
}

const IconWrapper = styled("div")<IconWrapper>(({ isOpened, type, theme }) => ({
    position: "absolute",
    bottom: 0,
    fontSize: 0,
    lineHeight: 0,
    height: "25px",
    width: "25px",
    transition: theme.transitions.create(["left", "right"], {
        duration: theme.transitions.duration.short,
        easing: theme.transitions.easing.easeInOut,
    }),
    zIndex: 100,
    cursor: "pointer",
    color: "black",
    [type === PanelSide.Right ? "right" : "left"]: isOpened ? PANEL_WIDTH : 0,
}));

export default function SidePanelToggleButton(props: Props) {
    const { t } = useTranslation();
    const { isOpened, onToggle, type } = props;
    const left = type === PanelSide.Left ? isOpened : !isOpened;
    const title = type === PanelSide.Left ? t("panel.toggle.left", "toggle left panel") : t("panel.toggle.right", "toggle right panel");

    return (
        <IconWrapper title={title} type={type} isOpened={isOpened} onClick={onToggle}>
            {left ? <LeftIcon /> : <RightIcon />}
        </IconWrapper>
    );
}
