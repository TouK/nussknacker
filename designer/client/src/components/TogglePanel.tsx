import React from "react";
import { useTranslation } from "react-i18next";
import LeftIcon from "../assets/img/arrows/arrow-left.svg";
import RightIcon from "../assets/img/arrows/arrow-right.svg";
import { styled } from "@mui/material";

const PANEL_WIDTH = 298;
type TogglePanelType = "RIGHT" | "LEFT";

interface IconWrapper {
    type: TogglePanelType;
    isOpened: boolean;
}
interface Props {
    isOpened: boolean;
    onToggle: () => void;
    type: TogglePanelType;
}

function validRightProps(props: IconWrapper) {
    if (props.type === "RIGHT") {
        return props.isOpened ? PANEL_WIDTH : 0;
    }
}

function validLeftProps(props: IconWrapper) {
    if (props.type === "LEFT") {
        return props.isOpened ? PANEL_WIDTH : 0;
    }
}

const IconWrapper = styled("div")((props: IconWrapper) => ({
    position: "absolute",
    bottom: 0,
    fontSize: 0,
    lineHeight: 0,
    height: "25px",
    width: "25px",
    transition: "left 0.5s ease, right 0.5s ease",
    zIndex: 100,
    cursor: "pointer",
    color: "black",
    right: validRightProps(props),
    left: validLeftProps(props),
}));

export default function TogglePanel(props: Props): JSX.Element {
    const { t } = useTranslation();
    const { isOpened, onToggle, type } = props;
    const left = type === "LEFT" ? isOpened : !isOpened;
    const title = type === "LEFT" ? t("panel.toggle.left", "toggle left panel") : t("panel.toggle.right", "toggle right panel");

    return (
        <IconWrapper title={title} type={type} isOpened={isOpened} onClick={onToggle}>
            {left ? <LeftIcon /> : <RightIcon />}
        </IconWrapper>
    );
}
