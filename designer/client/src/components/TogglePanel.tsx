import React from "react";
import { useTranslation } from "react-i18next";
import LeftIcon from "../assets/img/arrows/arrow-left.svg";
import RightIcon from "../assets/img/arrows/arrow-right.svg";
import styled from "@emotion/styled";

const panelWidth = 298;
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

function validRightProps(props: IconWrapper, panelWidth: number) {
    if (props.type === "RIGHT" && !props.isOpened) {
        return 0;
    } else if (props.type === "RIGHT" && props.isOpened) {
        return panelWidth;
    }
}

function validLeftProps(props: IconWrapper, panelWidth: number) {
    if (props.type === "LEFT" && !props.isOpened) {
        return 0;
    } else if (props.type === "LEFT" && props.isOpened) {
        return panelWidth;
    }
}

const IconWrapper = styled.div((props: IconWrapper) => ({
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
    right: validRightProps(props, panelWidth),
    left: validLeftProps(props, panelWidth),
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
